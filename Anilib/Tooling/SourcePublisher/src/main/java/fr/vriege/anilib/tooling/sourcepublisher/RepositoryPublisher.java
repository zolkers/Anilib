package fr.vriege.anilib.tooling.sourcepublisher;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.Signature;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.Objects;

final class RepositoryPublisher {
    private static final String DESCRIPTOR = "META-INF/anilib-extension.properties";

    private RepositoryPublisher() {
    }

    static void publish(Path privateKeyFile, Path outputDirectory, List<Path> configurationFiles) {
        if (configurationFiles.isEmpty()) {
            throw new IllegalArgumentException("At least one package configuration is required");
        }
        PrivateKey privateKey = PublisherKeys.privateKey(privateKeyFile);
        Path output = outputDirectory.toAbsolutePath().normalize();
        List<PublishedPackage> packages = configurationFiles.stream()
                .map(SourcePackageConfiguration::read)
                .sorted(Comparator.comparing(SourcePackageConfiguration::packageName))
                .map(configuration -> publishPackage(configuration, privateKey, output))
                .toList();
        Set<String> identities = new HashSet<>();
        if (packages.stream().anyMatch(value -> !identities.add(value.configuration().packageName()))) {
            throw new IllegalArgumentException("Package configurations must use unique package names");
        }
        PublisherFiles.writeText(output.resolve("index.min.json"), RepositoryJson.minified(packages));
        PublisherFiles.writeText(output.resolve("index.json"), RepositoryJson.pretty(packages));
        PublisherFiles.writeText(output.resolve("checksums.sha256"), checksums(packages));
    }

    private static PublishedPackage publishPackage(
            SourcePackageConfiguration configuration,
            PrivateKey privateKey,
            Path output) {
        byte[] bundle = PublisherFiles.read(configuration.bundle(), "source Bundle");
        verifyDescriptor(configuration, bundle);
        String sha256 = sha256(bundle);
        String signature = sign(bundle, privateKey);
        PublisherFiles.write(output.resolve(configuration.artifactName()), bundle);
        Optional<String> apkSha256 = configuration.apk().map(path -> publishApk(configuration, path, output));
        return new PublishedPackage(configuration, sha256, signature, apkSha256);
    }

    private static String publishApk(
            SourcePackageConfiguration configuration,
            Path apkPath,
            Path output) {
        byte[] apk = PublisherFiles.read(apkPath, "Android fallback APK");
        if (apk.length == 0) {
            throw new IllegalArgumentException("Android fallback APK must not be empty");
        }
        PublisherFiles.write(output.resolve("apk").resolve(configuration.apkArtifactName()), apk);
        return sha256(apk);
    }

    private static String checksums(List<PublishedPackage> packages) {
        StringBuilder result = new StringBuilder();
        for (PublishedPackage published : packages) {
            SourcePackageConfiguration configuration = published.configuration();
            result.append(published.sha256())
                    .append("  ")
                    .append(configuration.artifactName())
                    .append(System.lineSeparator());
            published.apkSha256().ifPresent(checksum -> result.append(checksum)
                    .append("  apk/")
                    .append(configuration.apkArtifactName())
                    .append(System.lineSeparator()));
        }
        return result.toString();
    }

    private static void verifyDescriptor(SourcePackageConfiguration configuration, byte[] bundle) {
        Properties descriptor = descriptor(bundle);
        same(configuration.packageName(), descriptor.getProperty("package"), "package");
        same(Long.toString(configuration.versionCode()), descriptor.getProperty("versionCode"), "versionCode");
        same(configuration.apiVersion(), descriptor.getProperty("api"), "api");
        same(Integer.toString(configuration.sources().size()), descriptor.getProperty("source.count"), "source.count");
        for (int index = 0; index < configuration.sources().size(); index++) {
            SourcePackageConfiguration.SourceEntry source = configuration.sources().get(index);
            String prefix = "source." + index + ".";
            same(source.id(), descriptor.getProperty(prefix + "id"), prefix + "id");
            same(source.name(), descriptor.getProperty(prefix + "name"), prefix + "name");
            same(source.factoryClass(), descriptor.getProperty(prefix + "factory"), prefix + "factory");
        }
    }

    private static Properties descriptor(byte[] bundle) {
        try (ZipInputStream archive = new ZipInputStream(new ByteArrayInputStream(bundle))) {
            ZipEntry entry;
            while ((entry = archive.getNextEntry()) != null) {
                if (DESCRIPTOR.equals(entry.getName()) && !entry.isDirectory()) {
                    Properties properties = new Properties();
                    properties.load(new StringReader(new String(archive.readAllBytes(), StandardCharsets.UTF_8)));
                    return properties;
                }
            }
        } catch (IOException exception) {
            throw new IllegalArgumentException("Source Bundle is not a readable JAR", exception);
        }
        throw new IllegalArgumentException("Source Bundle does not contain " + DESCRIPTOR);
    }

    private static void same(String configured, String bundled, String name) {
        if (!configured.equals(bundled)) {
            throw new IllegalArgumentException("Bundle descriptor does not match publisher field " + name);
        }
    }

    private static String sha256(byte[] bundle) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bundle));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("JDK 21 does not provide SHA-256", exception);
        }
    }

    private static String sign(byte[] bundle, PrivateKey privateKey) {
        try {
            Signature signer = Signature.getInstance("Ed25519");
            signer.initSign(privateKey);
            signer.update(bundle);
            return Base64.getEncoder().encodeToString(signer.sign());
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Unable to sign source Bundle", exception);
        }
    }

    record PublishedPackage(
            SourcePackageConfiguration configuration,
            String sha256,
            String signature,
            Optional<String> apkSha256) {
        PublishedPackage {
            apkSha256 = Objects.requireNonNull(apkSha256, "apkSha256 must not be null");
        }
    }
}
