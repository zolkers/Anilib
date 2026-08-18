package fr.vriege.anilib.tooling.archtests;

import fr.vriege.anilib.feature.extensionrepository.ExtensionArtifactFormat;
import fr.vriege.anilib.feature.extensionrepository.ExtensionArtifactMetadata;
import fr.vriege.anilib.feature.extensionrepository.ExtensionContentKind;
import fr.vriege.anilib.feature.extensionrepository.ExtensionInstallationState;
import fr.vriege.anilib.feature.extensionrepository.ExtensionPackageMetadata;
import fr.vriege.anilib.feature.extensionrepository.ExtensionRepositorySnapshot;
import fr.vriege.anilib.feature.extensionrepository.ExtensionSourceMetadata;
import fr.vriege.anilib.feature.extensionrepository.InstalledExtensionPackage;
import fr.vriege.anilib.feature.extensionrepository.runtime.AniyomiRepositoryIndexParser;
import fr.vriege.anilib.feature.extensionrepository.runtime.DefaultExtensionInstallationService;
import fr.vriege.anilib.feature.extensionrepository.runtime.DefaultExtensionRepositoryService;
import fr.vriege.anilib.feature.extensionrepository.runtime.FileExtensionTrustStore;
import fr.vriege.anilib.feature.extensionrepository.runtime.FileExtensionRepositoryStore;
import fr.vriege.anilib.feature.extensionrepository.runtime.FileInstalledExtensionStore;
import fr.vriege.anilib.feature.extensionrepository.ui.LegacyExtensionCompatibility;
import fr.vriege.anilib.feature.extensionrepository.ui.LegacyExtensionInstallers;
import fr.vriege.anilib.feature.extensionrepository.ui.LegacyExtensionPackage;
import fr.vriege.anilib.feature.extensionrepository.ui.LegacyExtensionRuntimeReport;
import fr.vriege.anilib.feature.extensionrepository.ui.LegacyExtensionRuntimeState;
import fr.vriege.anilib.framework.http.AnilibHttpClient;
import fr.vriege.anilib.framework.http.HttpRequest;
import fr.vriege.anilib.framework.http.HttpResponse;

import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.Signature;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Base64;
import java.util.HexFormat;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Behavior checks for user-supplied Aniyomi-compatible extension repositories. */
final class ExtensionRepositoryTest {
    private static final URI INDEX = URI.create("https://repo.example/extensions/index.min.json");
    private static final URI BUNDLE = URI.create("https://repo.example/extensions/example.jar");
    private static final String SHA_256 = "0123456789abcdef0123456789abcdef"
            + "0123456789abcdef0123456789abcdef";

    private ExtensionRepositoryTest() {
    }

    static int run() {
        Counter counter = new Counter();
        parsesAniyomiAndPortableArtifacts(counter);
        rejectsUnsafeMetadata(counter);
        persistsAndRefreshesUserRepositories(counter);
        installsOnlyTrustedPortableBundles(counter);
        modelsLegacyAndroidDiscovery(counter);
        return counter.value;
    }

    private static void parsesAniyomiAndPortableArtifacts(Counter counter) {
        String index = """
                [{
                  "name":"Aniyomi: Example",
                  "pkg":"eu.kanade.tachiyomi.animeextension.en.example",
                  "apk":"example-v14.2.apk",
                  "lang":"en",
                  "code":2,
                  "version":"14.2",
                  "nsfw":0,
                  "anilib":{
                    "bundle":"example-v1.2.jar",
                    "api":"1.4",
                    "sha256":"%s",
                    "signature":"c2lnbmF0dXJl",
                    "keyId":"example-key",
                    "kind":"anime"
                  },
                  "sources":[{
                    "name":"Example",
                    "lang":"en",
                    "id":"1234567890123456789",
                    "baseUrl":""
                  }]
                }]
                """.formatted(SHA_256);
        List<ExtensionPackageMetadata> packages = new AniyomiRepositoryIndexParser().parse(INDEX, index);
        ExtensionPackageMetadata extension = packages.getFirst();
        counter.check(extension.artifacts().size() == 2,
                "one compatible index entry must expose APK and portable Bundle artifacts");
        counter.check(extension.artifacts().getFirst().format() == ExtensionArtifactFormat.ANIYOMI_APK,
                "Aniyomi apk metadata must remain identifiable");
        counter.check(extension.artifacts().get(1).sha256().orElseThrow().equals(SHA_256),
                "portable Bundle metadata must retain its checksum");
        counter.check(extension.sources().getFirst().baseUri().isEmpty(),
                "Aniyomi indexes may advertise a source with an empty baseUrl");
    }

    private static void rejectsUnsafeMetadata(Counter counter) {
        AniyomiRepositoryIndexParser parser = new AniyomiRepositoryIndexParser();
        counter.expectIllegalArgument(
                () -> parser.parse(URI.create("http://repo.example/index.json"), "[]"),
                "repository indexes must require HTTPS");
        counter.expectIllegalArgument(
                () -> parser.parse(INDEX, """
                        [{"name":"Bad","pkg":"invalid","apk":"bad.apk","lang":"en",
                        "code":1,"version":"1","sources":[]}]
                        """),
                "repository entries must reject invalid package identities and empty sources");
        counter.expectIllegalArgument(
                () -> parser.parse(INDEX, """
                        [{"name":"Bad","pkg":"a.b","apk":"http://bad.example/a.apk","lang":"en",
                        "code":1,"version":"1","sources":[{"name":"Bad","lang":"en","id":"1"}]}]
                        """),
                "repository artifacts must reject cleartext downloads");
    }

    private static void persistsAndRefreshesUserRepositories(Counter counter) {
        Path directory = temporaryDirectory();
        try {
            Path storePath = directory.resolve("repositories.txt");
            String index = """
                    [{"name":"Example","pkg":"eu.example.extension","apk":"example.apk",
                    "lang":"all","code":4,"version":"1.4","nsfw":1,
                    "sources":[{"name":"Example","lang":"all","id":"42",
                    "baseUrl":"https://source.example"}]}]
                    """;
            RecordingClient client = new RecordingClient(index);
            DefaultExtensionRepositoryService service = new DefaultExtensionRepositoryService(
                    new FileExtensionRepositoryStore(storePath),
                    client,
                    Clock.fixed(Instant.parse("2026-08-18T10:00:00Z"), ZoneOffset.UTC),
                    new AniyomiRepositoryIndexParser());
            counter.check(service.repositories().isEmpty(),
                    "new products must not receive a bundled third-party repository");
            service.add(INDEX);
            ExtensionRepositorySnapshot snapshot = service.refresh(INDEX);
            counter.check(snapshot.successful() && snapshot.packages().size() == 1,
                    "configured repository must refresh into a bounded catalogue");
            counter.check(client.lastRequest.uri().equals(INDEX)
                            && client.lastRequest.headers().get("accept").contains("application/json"),
                    "repository refresh must use the shared HTTP client and explicit JSON acceptance");
            counter.check(service.packages().getFirst().adult(),
                    "Aniyomi nsfw metadata must survive catalogue refresh");
            DefaultExtensionRepositoryService reopened = new DefaultExtensionRepositoryService(
                    new FileExtensionRepositoryStore(storePath),
                    client);
            counter.check(reopened.repositories().equals(List.of(INDEX)),
                    "user repository URLs must survive product restart");
            counter.check(reopened.remove(INDEX) && reopened.repositories().isEmpty(),
                    "users must be able to remove their own repository URL");
        } finally {
            deleteDirectory(directory);
        }
    }

    private static void installsOnlyTrustedPortableBundles(Counter counter) {
        Path directory = temporaryDirectory();
        try {
            KeyPair keyPair = keyPair();
            byte[] versionOne = bundle("eu.example.extension", 1, "1.4");
            ExtensionPackageMetadata first = portablePackage(versionOne, keyPair, 1);
            DefaultExtensionInstallationService service = installationService(
                    directory,
                    new RecordingClient(versionOne));
            service.trust("example-publisher", Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded()));
            InstalledExtensionPackage installed = service.install(first);
            counter.check(installed.state() == ExtensionInstallationState.ENABLED
                            && service.installed().equals(List.of(installed)),
                    "a checksum-matched Bundle signed by a trusted publisher must install enabled");
            service.setEnabled(first.packageName(), false);
            counter.check(service.installed().getFirst().state() == ExtensionInstallationState.DISABLED,
                    "installed portable Bundles must support durable disable state");

            byte[] versionTwo = bundle("eu.example.extension", 2, "1.4");
            DefaultExtensionInstallationService reopened = installationService(
                    directory,
                    new RecordingClient(versionTwo));
            InstalledExtensionPackage updated = reopened.update(portablePackage(versionTwo, keyPair, 2));
            counter.check(updated.versionCode() == 2
                            && updated.state() == ExtensionInstallationState.DISABLED,
                    "a verified newer Bundle must update without silently enabling a disabled extension");
            counter.check(reopened.remove(first.packageName()) && reopened.installed().isEmpty(),
                    "users must be able to remove installed portable Bundles");

            Path untrustedDirectory = directory.resolve("untrusted");
            DefaultExtensionInstallationService untrusted = installationService(
                    untrustedDirectory,
                    new RecordingClient(versionOne));
            counter.expectSecurity(
                    () -> untrusted.install(first),
                    "portable Bundles from untrusted signing keys must not install");
            counter.check(new DefaultExtensionInstallationService(
                            directory,
                            new RecordingClient(versionTwo)).installed().isEmpty(),
                    "installed-extension removal must survive product restart");
        } finally {
            deleteDirectory(directory);
        }
    }

    private static DefaultExtensionInstallationService installationService(
            Path directory,
            RecordingClient client) {
        return new DefaultExtensionInstallationService(
                directory,
                client,
                Clock.fixed(Instant.parse("2026-08-18T12:00:00Z"), ZoneOffset.UTC),
                new FileInstalledExtensionStore(directory.resolve("installed.tsv")),
                new FileExtensionTrustStore(directory.resolve("trusted-keys.txt")));
    }

    private static void modelsLegacyAndroidDiscovery(Counter counter) {
        LegacyExtensionPackage extension = new LegacyExtensionPackage(
                "eu.kanade.tachiyomi.animeextension.en.example",
                "Example",
                7,
                "16.7",
                "16.0",
                false,
                false,
                List.of("eu.kanade.tachiyomi.animeextension.en.example.Example"),
                Optional.of("eu.kanade.tachiyomi.animeextension.en.example.ExampleFactory"),
                true,
                true,
                List.of(SHA_256),
                LegacyExtensionCompatibility.COMPATIBLE_METADATA);
        counter.check(extension.sourceEntrypoints().size() == 1
                        && extension.sourceFactory().isPresent()
                        && extension.hasReadme()
                        && extension.compatibility() == LegacyExtensionCompatibility.COMPATIBLE_METADATA,
                "Android discovery metadata must retain the Aniyomi extension contract");
        counter.check(LegacyExtensionInstallers.unavailable().discoverInstalled().isEmpty(),
                "platforms without APK support must expose an empty legacy inventory");
        LegacyExtensionRuntimeReport preflight = new LegacyExtensionRuntimeReport(
                extension.packageName(),
                LegacyExtensionRuntimeState.HOST_ABI_MISSING,
                List.of("rx.Observable", "eu.kanade.tachiyomi.animesource.AnimeSource"),
                Optional.of(SHA_256));
        counter.check(preflight.missingHostClasses().getFirst()
                        .equals("eu.kanade.tachiyomi.animesource.AnimeSource")
                        && preflight.trustedCertificateSha256().orElseThrow().equals(SHA_256),
                "legacy runtime preflight must retain deterministic ABI and certificate evidence");
        counter.check(LegacyExtensionInstallers.unavailable().runtimeReport(extension).state()
                        == LegacyExtensionRuntimeState.UNSUPPORTED_PLATFORM,
                "platforms without an APK runtime must report it explicitly");
        counter.expectIllegalArgument(
                () -> new LegacyExtensionRuntimeReport(
                        extension.packageName(),
                        LegacyExtensionRuntimeState.HOST_ABI_MISSING,
                        List.of(),
                        Optional.of(SHA_256)),
                "missing-host-ABI reports must identify at least one absent class");
        counter.expectIllegalArgument(
                () -> new LegacyExtensionPackage(
                        " ",
                        "Example",
                        1,
                        "16.1",
                        "16.0",
                        false,
                        false,
                        List.of(),
                        Optional.empty(),
                        false,
                        false,
                        List.of(),
                        LegacyExtensionCompatibility.MISSING_ENTRYPOINT),
                "legacy extension metadata must reject blank package identities");
    }

    private static ExtensionPackageMetadata portablePackage(byte[] bundle, KeyPair keyPair, long versionCode) {
        String checksum = sha256(bundle);
        String signature = signature(bundle, keyPair);
        return new ExtensionPackageMetadata(
                "Example",
                "eu.example.extension",
                "en",
                versionCode,
                "1." + versionCode,
                false,
                ExtensionContentKind.MIXED,
                List.of(new ExtensionSourceMetadata("Example", "en", "42", Optional.of(BUNDLE))),
                List.of(new ExtensionArtifactMetadata(
                        ExtensionArtifactFormat.ANILIB_BUNDLE,
                        BUNDLE,
                        Optional.of(checksum),
                        Optional.of(signature),
                        Optional.of("example-publisher"),
                        Optional.of("1.4"))));
    }

    private static byte[] bundle(String packageName, long versionCode, String api) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (ZipOutputStream archive = new ZipOutputStream(bytes)) {
                archive.putNextEntry(new ZipEntry("META-INF/anilib-extension.properties"));
                archive.write(("package=" + packageName
                        + "\nversionCode=" + versionCode
                        + "\napi=" + api
                        + "\nmodule=" + packageName
                        + "\nsource.count=1"
                        + "\nsource.0.id=example.source"
                        + "\nsource.0.component=extension.example.source"
                        + "\nsource.0.name=Example"
                        + "\nsource.0.factory=" + packageName + ".ExampleFactory\n")
                        .getBytes(StandardCharsets.UTF_8));
                archive.closeEntry();
            }
            return bytes.toByteArray();
        } catch (IOException exception) {
            throw new AssertionError("Unable to create portable Bundle fixture", exception);
        }
    }

    private static KeyPair keyPair() {
        try {
            return KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        } catch (GeneralSecurityException exception) {
            throw new AssertionError("JDK must provide Ed25519", exception);
        }
    }

    private static String signature(byte[] bytes, KeyPair keyPair) {
        try {
            Signature signature = Signature.getInstance("Ed25519");
            signature.initSign(keyPair.getPrivate());
            signature.update(bytes);
            return Base64.getEncoder().encodeToString(signature.sign());
        } catch (GeneralSecurityException exception) {
            throw new AssertionError("Unable to sign portable Bundle fixture", exception);
        }
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (GeneralSecurityException exception) {
            throw new AssertionError("JDK must provide SHA-256", exception);
        }
    }

    private static Path temporaryDirectory() {
        try {
            return Files.createTempDirectory("anilib-extension-repository-test");
        } catch (IOException exception) {
            throw new AssertionError("Unable to create extension repository test directory", exception);
        }
    }

    private static void deleteDirectory(Path directory) {
        if (!Files.exists(directory)) {
            return;
        }
        try (java.util.stream.Stream<Path> entries = Files.walk(directory)) {
            for (Path entry : entries.sorted(java.util.Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(entry);
            }
        } catch (IOException exception) {
            throw new AssertionError("Unable to clean extension repository test directory", exception);
        }
    }

    private static final class RecordingClient implements AnilibHttpClient {
        private final byte[] body;
        private HttpRequest lastRequest;

        private RecordingClient(String body) {
            this.body = body.getBytes(StandardCharsets.UTF_8);
        }

        private RecordingClient(byte[] body) {
            this.body = body.clone();
        }

        @Override
        public HttpResponse execute(HttpRequest request) {
            lastRequest = request;
            return new HttpResponse(200, Map.of("content-type", List.of("application/json")), body, false);
        }
    }

    private static final class Counter {
        private int value;

        private void check(boolean condition, String message) {
            value++;
            if (!condition) {
                throw new AssertionError(message);
            }
        }

        private void expectIllegalArgument(Runnable action, String message) {
            try {
                action.run();
                throw new AssertionError(message);
            } catch (IllegalArgumentException expected) {
                value++;
            }
        }

        private void expectSecurity(Runnable action, String message) {
            try {
                action.run();
                throw new AssertionError(message);
            } catch (SecurityException expected) {
                value++;
            }
        }
    }
}
