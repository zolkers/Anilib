package fr.vriege.anilib.feature.extensionrepository.runtime;

import fr.vriege.anilib.feature.extensionrepository.ExtensionArtifactFormat;
import fr.vriege.anilib.feature.extensionrepository.ExtensionArtifactMetadata;
import fr.vriege.anilib.feature.extensionrepository.ExtensionBundleLoadFailure;
import fr.vriege.anilib.feature.extensionrepository.ExtensionInstallationService;
import fr.vriege.anilib.feature.extensionrepository.ExtensionInstallationState;
import fr.vriege.anilib.feature.extensionrepository.ExtensionPackageMetadata;
import fr.vriege.anilib.feature.extensionrepository.InstalledExtensionPackage;
import fr.vriege.anilib.feature.source.SourceApiVersion;
import fr.vriege.anilib.feature.source.SourceSdk;
import fr.vriege.anilib.framework.http.AnilibHttpClient;
import fr.vriege.anilib.framework.http.HttpCachePolicy;
import fr.vriege.anilib.framework.http.HttpRequest;
import fr.vriege.anilib.framework.http.HttpResponse;
import fr.vriege.anilib.foundation.validation.Preconditions;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.Signature;
import java.security.SignatureException;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Verified portable-Bundle lifecycle using only JDK cryptography and storage. */
public final class DefaultExtensionInstallationService implements ExtensionInstallationService {
    private static final int MAX_ARTIFACT_BYTES = 16 * 1024 * 1024;
    private static final int MAX_REDIRECTS = 5;
    private static final Duration MINIMUM_INTERVAL = Duration.ofMillis(250);
    private static final HexFormat HEX = HexFormat.of();

    private final Path artifactDirectory;
    private final FileInstalledExtensionStore installedStore;
    private final FileExtensionTrustStore trustStore;
    private final AnilibHttpClient client;
    private final Clock clock;
    private final Map<String, InstalledExtensionPackage> installed;
    private final Map<String, PublicKey> trustedKeys;
    private final List<ExtensionBundleLoadFailure> loadFailures;

    public DefaultExtensionInstallationService(
            Path installationDirectory,
            AnilibHttpClient client) {
        this(installationDirectory, client, List.of());
    }

    public DefaultExtensionInstallationService(
            Path installationDirectory,
            AnilibHttpClient client,
            List<ExtensionBundleLoadFailure> loadFailures) {
        this(
                installationDirectory,
                client,
                Clock.systemUTC(),
                new FileInstalledExtensionStore(installationDirectory.resolve("installed.tsv")),
                new FileExtensionTrustStore(installationDirectory.resolve("trusted-keys.txt")),
                loadFailures);
    }

    public DefaultExtensionInstallationService(
            Path installationDirectory,
            AnilibHttpClient client,
            Clock clock,
            FileInstalledExtensionStore installedStore,
            FileExtensionTrustStore trustStore) {
        this(installationDirectory, client, clock, installedStore, trustStore, List.of());
    }

    public DefaultExtensionInstallationService(
            Path installationDirectory,
            AnilibHttpClient client,
            Clock clock,
            FileInstalledExtensionStore installedStore,
            FileExtensionTrustStore trustStore,
            List<ExtensionBundleLoadFailure> loadFailures) {
        Path root = Preconditions.requireNonNull(installationDirectory, "installationDirectory")
                .toAbsolutePath()
                .normalize();
        artifactDirectory = root.resolve("artifacts");
        this.client = Preconditions.requireNonNull(client, "client");
        this.clock = Preconditions.requireNonNull(clock, "clock");
        this.installedStore = Preconditions.requireNonNull(installedStore, "installedStore");
        this.trustStore = Preconditions.requireNonNull(trustStore, "trustStore");
        installed = new LinkedHashMap<>(installedStore.load());
        trustedKeys = new LinkedHashMap<>(trustStore.load());
        this.loadFailures = List.copyOf(Preconditions.requireNonNull(loadFailures, "loadFailures"));
    }

    @Override
    public synchronized List<InstalledExtensionPackage> installed() {
        return installed.values().stream()
                .sorted(Comparator.comparing(InstalledExtensionPackage::displayName)
                        .thenComparing(InstalledExtensionPackage::packageName))
                .toList();
    }

    @Override
    public List<ExtensionBundleLoadFailure> loadFailures() {
        return loadFailures;
    }

    @Override
    public synchronized List<String> trustedKeyIds() {
        return trustedKeys.keySet().stream().sorted().toList();
    }

    @Override
    public synchronized void trust(String keyId, String x509PublicKeyBase64) {
        trustedKeys.put(
                FileExtensionTrustStore.requireKeyId(keyId),
                FileExtensionTrustStore.decode(x509PublicKeyBase64));
        trustStore.save(trustedKeys);
    }

    @Override
    public synchronized boolean forgetTrust(String keyId) {
        String normalized = FileExtensionTrustStore.requireKeyId(keyId);
        if (trustedKeys.remove(normalized) == null) {
            return false;
        }
        trustStore.save(trustedKeys);
        return true;
    }

    @Override
    public synchronized InstalledExtensionPackage install(ExtensionPackageMetadata extensionPackage) {
        ExtensionPackageMetadata metadata = Preconditions.requireNonNull(extensionPackage, "extensionPackage");
        if (installed.containsKey(metadata.packageName())) {
            throw new IllegalStateException("Extension is already installed: " + metadata.packageName());
        }
        return verifiedInstall(metadata, ExtensionInstallationState.ENABLED);
    }

    @Override
    public synchronized InstalledExtensionPackage update(ExtensionPackageMetadata extensionPackage) {
        ExtensionPackageMetadata metadata = Preconditions.requireNonNull(extensionPackage, "extensionPackage");
        InstalledExtensionPackage current = installed.get(metadata.packageName());
        if (current == null) {
            throw new IllegalStateException("Extension is not installed: " + metadata.packageName());
        }
        if (metadata.versionCode() <= current.versionCode()) {
            throw new IllegalArgumentException("Extension update version must be newer than the installed version");
        }
        InstalledExtensionPackage updated = verifiedInstall(metadata, current.state());
        deleteArtifact(current);
        return updated;
    }

    @Override
    public synchronized InstalledExtensionPackage setEnabled(String packageName, boolean enabled) {
        InstalledExtensionPackage current = requireInstalled(packageName);
        InstalledExtensionPackage updated = new InstalledExtensionPackage(
                current.packageName(),
                current.displayName(),
                current.versionCode(),
                current.versionName(),
                current.format(),
                enabled ? ExtensionInstallationState.ENABLED : ExtensionInstallationState.DISABLED,
                current.sha256(),
                current.signingKeyId(),
                current.installedAt());
        installed.put(current.packageName(), updated);
        installedStore.save(installed);
        return updated;
    }

    @Override
    public synchronized boolean remove(String packageName) {
        InstalledExtensionPackage current = installed.remove(Preconditions.requireNonBlank(packageName, "packageName"));
        if (current == null) {
            return false;
        }
        installedStore.save(installed);
        deleteArtifact(current);
        return true;
    }

    private InstalledExtensionPackage verifiedInstall(
            ExtensionPackageMetadata metadata,
            ExtensionInstallationState state) {
        ExtensionArtifactMetadata artifact = portableArtifact(metadata);
        SourceApiVersion requiredApi = requiredApi(artifact);
        if (!SourceSdk.API_VERSION.supports(requiredApi)) {
            throw new IllegalArgumentException(
                    "Extension requires Source API " + requiredApi + " but Anilib provides " + SourceSdk.API_VERSION);
        }
        byte[] bytes = fetch(artifact.uri());
        String checksum = checksum(bytes);
        String expectedChecksum = artifact.sha256()
                .orElseThrow(() -> new IllegalArgumentException("Portable Bundle must declare sha256"));
        if (!MessageDigest.isEqual(
                checksum.getBytes(StandardCharsets.US_ASCII),
                expectedChecksum.getBytes(StandardCharsets.US_ASCII))) {
            throw new SecurityException("Portable Bundle SHA-256 does not match repository metadata");
        }
        verifySignature(bytes, artifact);
        verifyDescriptor(bytes, metadata, requiredApi);
        InstalledExtensionPackage result = new InstalledExtensionPackage(
                metadata.packageName(),
                metadata.displayName(),
                metadata.versionCode(),
                metadata.versionName(),
                ExtensionArtifactFormat.ANILIB_BUNDLE,
                state,
                checksum,
                artifact.signingKeyId(),
                clock.instant());
        writeArtifact(result, bytes);
        installed.put(result.packageName(), result);
        installedStore.save(installed);
        return result;
    }

    private ExtensionArtifactMetadata portableArtifact(ExtensionPackageMetadata metadata) {
        return metadata.artifacts().stream()
                .filter(artifact -> artifact.format() == ExtensionArtifactFormat.ANILIB_BUNDLE)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Extension has no portable Anilib Bundle"));
    }

    private SourceApiVersion requiredApi(ExtensionArtifactMetadata artifact) {
        String value = artifact.requiredApiVersion()
                .orElseThrow(() -> new IllegalArgumentException("Portable Bundle must declare api"));
        String[] parts = value.split("\\.", -1);
        if (parts.length != 2) {
            throw new IllegalArgumentException("Portable Bundle api must use major.minor syntax");
        }
        try {
            return new SourceApiVersion(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Portable Bundle api must use major.minor syntax", exception);
        }
    }

    private byte[] fetch(URI initialUri) {
        URI current = requireHttps(initialUri);
        for (int redirects = 0; redirects <= MAX_REDIRECTS; redirects++) {
            HttpRequest request = HttpRequest.builder(current)
                    .header("accept", "application/java-archive, application/octet-stream")
                    .cache(HttpCachePolicy.bypass())
                    .minimumInterval(MINIMUM_INTERVAL)
                    .build();
            HttpResponse response = client.execute(request);
            if (!redirect(response.statusCode())) {
                if (response.statusCode() != 200) {
                    throw new IllegalStateException("Extension artifact returned HTTP " + response.statusCode());
                }
                byte[] body = response.body();
                if (body.length > MAX_ARTIFACT_BYTES) {
                    throw new IllegalArgumentException("Extension artifact exceeds 16 MiB");
                }
                return body;
            }
            if (redirects == MAX_REDIRECTS) {
                throw new IllegalStateException("Extension artifact exceeded " + MAX_REDIRECTS + " redirects");
            }
            String location = response.firstHeader("location")
                    .orElseThrow(() -> new IllegalStateException("Extension redirect has no Location header"));
            current = requireHttps(current.resolve(location));
        }
        throw new IllegalStateException("Unreachable extension redirect state");
    }

    private void verifySignature(byte[] bytes, ExtensionArtifactMetadata artifact) {
        String keyId = artifact.signingKeyId()
                .orElseThrow(() -> new SecurityException("Portable Bundle must declare a signing key"));
        PublicKey key = trustedKeys.get(keyId);
        if (key == null) {
            throw new SecurityException("Extension signing key is not trusted: " + keyId);
        }
        String encoded = artifact.signature()
                .orElseThrow(() -> new SecurityException("Portable Bundle must declare a signature"));
        if (encoded.length() > 512) {
            throw new SecurityException("Extension signature is too large");
        }
        try {
            byte[] signatureBytes = Base64.getDecoder().decode(encoded);
            Signature verifier = Signature.getInstance("Ed25519");
            verifier.initVerify(key);
            verifier.update(bytes);
            if (!verifier.verify(signatureBytes)) {
                throw new SecurityException("Portable Bundle signature is invalid");
            }
        } catch (IllegalArgumentException | InvalidKeyException | SignatureException exception) {
            throw new SecurityException("Portable Bundle signature cannot be verified", exception);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JDK does not provide Ed25519", exception);
        }
    }

    private void verifyDescriptor(
            byte[] bytes,
            ExtensionPackageMetadata metadata,
            SourceApiVersion requiredApi) {
        PortableSourceBundleDescriptor descriptor = PortableSourceBundleDescriptor.read(bytes);
        if (!metadata.packageName().equals(descriptor.packageName())) {
            throw new SecurityException("Portable Bundle descriptor package does not match repository metadata");
        }
        if (metadata.versionCode() != descriptor.versionCode()) {
            throw new SecurityException("Portable Bundle descriptor version does not match repository metadata");
        }
        if (!requiredApi.equals(descriptor.apiVersion())) {
            throw new SecurityException("Portable Bundle descriptor API does not match repository metadata");
        }
    }

    private void writeArtifact(InstalledExtensionPackage extension, byte[] bytes) {
        Path destination = artifactPath(extension);
        Path temporary = destination.resolveSibling(destination.getFileName() + ".tmp");
        try {
            Files.createDirectories(artifactDirectory);
            Files.write(temporary, bytes);
            try {
                Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            throw new UncheckedIOException("Unable to store verified extension artifact", exception);
        }
    }

    private void deleteArtifact(InstalledExtensionPackage extension) {
        try {
            Files.deleteIfExists(artifactPath(extension));
        } catch (IOException exception) {
            throw new UncheckedIOException("Unable to remove extension artifact", exception);
        }
    }

    private Path artifactPath(InstalledExtensionPackage extension) {
        return PortableExtensionArtifacts.path(artifactDirectory.getParent(), extension);
    }

    private InstalledExtensionPackage requireInstalled(String packageName) {
        String name = Preconditions.requireNonBlank(packageName, "packageName");
        InstalledExtensionPackage extension = installed.get(name);
        if (extension == null) {
            throw new IllegalArgumentException("Extension is not installed: " + name);
        }
        return extension;
    }

    private static URI requireHttps(URI value) {
        URI uri = Preconditions.requireNonNull(value, "uri").normalize();
        if (!"https".equalsIgnoreCase(uri.getScheme())
                || uri.getHost() == null
                || uri.getHost().isBlank()
                || uri.getUserInfo() != null
                || uri.getFragment() != null) {
            throw new IllegalArgumentException("Extension artifact URI must be absolute HTTPS without credentials");
        }
        return uri;
    }

    private static boolean redirect(int statusCode) {
        return statusCode == 301
                || statusCode == 302
                || statusCode == 303
                || statusCode == 307
                || statusCode == 308;
    }

    private static String checksum(byte[] bytes) {
        try {
            return HEX.formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JDK does not provide SHA-256", exception);
        }
    }
}
