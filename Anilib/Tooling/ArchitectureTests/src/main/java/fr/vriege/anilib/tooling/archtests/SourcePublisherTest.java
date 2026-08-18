package fr.vriege.anilib.tooling.archtests;

import fr.vriege.anilib.configuration.standard.StandardAnilib;
import fr.vriege.anilib.feature.extensionrepository.ExtensionPackageMetadata;
import fr.vriege.anilib.feature.extensionrepository.runtime.AniyomiRepositoryIndexParser;
import fr.vriege.anilib.feature.extensionrepository.runtime.DefaultExtensionInstallationService;
import fr.vriege.anilib.feature.source.CatalogueSource;
import fr.vriege.anilib.feature.source.SourceBrowseRequest;
import fr.vriege.anilib.feature.source.SourceCapabilities;
import fr.vriege.anilib.feature.source.SourceId;
import fr.vriege.anilib.framework.http.AnilibHttpClient;
import fr.vriege.anilib.framework.http.HttpRequest;
import fr.vriege.anilib.framework.http.HttpResponse;
import fr.vriege.anilib.kernel.StartedAnilib;
import fr.vriege.anilib.tooling.sourcepublisher.SourcePublisher;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** End-to-end verification of the official source template and publication toolchain. */
final class SourcePublisherTest {
    private SourcePublisherTest() {
    }

    static int run() {
        Path directory = temporaryDirectory();
        try {
            Path classes = requiredDirectoryProperty("anilib.example.source.classes");
            Path descriptor = requiredFileProperty("anilib.example.source.descriptor");
            Path configuration = requiredFileProperty("anilib.example.source.configuration");
            Path bundle = directory.resolve("anilib-example-source.jar");
            Path publishConfiguration = directory.resolve("source-publisher.properties");
            Path privateKey = directory.resolve("private.key");
            Path publicKey = directory.resolve("public.key");
            Path repository = directory.resolve("repository");
            SourcePublisher.pack(classes, descriptor, bundle);
            String configured = Files.readString(configuration, StandardCharsets.UTF_8)
                    .replace(
                            "bundle=build/libs/anilib-example-source.jar",
                            "bundle=" + bundle.toString().replace('\\', '/'));
            Files.writeString(publishConfiguration, configured, StandardCharsets.UTF_8);
            SourcePublisher.generateKeys(privateKey, publicKey);
            SourcePublisher.publish(privateKey, repository, List.of(publishConfiguration));
            check(Files.isRegularFile(bundle),
                    "SourcePublisher pack must build the official template Bundle");

            String index = Files.readString(repository.resolve("index.json"), StandardCharsets.UTF_8);
            List<ExtensionPackageMetadata> packages = new AniyomiRepositoryIndexParser().parse(
                    URI.create("https://sources.example/index.json"),
                    index);
            check(packages.size() == 1, "publisher must generate one parseable package index");
            ExtensionPackageMetadata metadata = packages.getFirst();
            check(metadata.packageName().startsWith("fr.vriege.anilib."),
                    "official source template must use the Anilib package namespace");

            byte[] bundleBytes = Files.readAllBytes(bundle);
            DefaultExtensionInstallationService installation = new DefaultExtensionInstallationService(
                    directory.resolve("extensions"),
                    new BundleClient(bundleBytes));
            installation.trust(
                    metadata.artifacts().getFirst().signingKeyId().orElseThrow(),
                    Files.readString(publicKey, StandardCharsets.UTF_8).strip());
            installation.install(metadata);
            check(installation.installed().size() == 1,
                    "published source Bundle must pass checksum, signature, API, and descriptor validation");

            try (StartedAnilib application = StandardAnilib.start(directory)) {
                CatalogueSource source = (CatalogueSource) application.capability(SourceCapabilities.REGISTRY)
                        .find(SourceId.of("example.catalogue"))
                        .orElseThrow();
                check(source.popular(new SourceBrowseRequest(1, 20, List.of(), Map.of())).items().size() == 3,
                        "official source must load through the shared Android/desktop product graph");
            }
            check(Files.isRegularFile(repository.resolve("index.min.json")),
                    "publisher must emit the minified dynamic index");
            return 6;
        } catch (IOException exception) {
            throw new AssertionError("Unable to verify source publication", exception);
        } finally {
            deleteDirectory(directory);
        }
    }

    private static Path requiredProperty(String name) {
        String value = System.getProperty(name);
        if (value == null || value.isBlank()) {
            throw new AssertionError("Architecture test property is missing: " + name);
        }
        Path path = Path.of(value).toAbsolutePath().normalize();
        return path;
    }

    private static Path requiredFileProperty(String name) {
        Path path = requiredProperty(name);
        if (!Files.isRegularFile(path)) {
            throw new AssertionError("Architecture test file is missing: " + path);
        }
        return path;
    }

    private static Path requiredDirectoryProperty(String name) {
        Path path = requiredProperty(name);
        if (!Files.isDirectory(path)) {
            throw new AssertionError("Architecture test directory is missing: " + path);
        }
        return path;
    }

    private static Path temporaryDirectory() {
        try {
            return Files.createTempDirectory("anilib-source-publisher-test");
        } catch (IOException exception) {
            throw new AssertionError("Unable to create source publisher test directory", exception);
        }
    }

    private static void deleteDirectory(Path directory) {
        if (!Files.exists(directory)) {
            return;
        }
        try (java.util.stream.Stream<Path> entries = Files.walk(directory)) {
            for (Path entry : entries.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(entry);
            }
        } catch (IOException exception) {
            throw new AssertionError("Unable to clean source publisher test directory", exception);
        }
    }

    private record BundleClient(byte[] bundle) implements AnilibHttpClient {
        private BundleClient {
            bundle = bundle.clone();
        }

        @Override
        public HttpResponse execute(HttpRequest request) {
            return new HttpResponse(
                    200,
                    Map.of("content-type", List.of("application/java-archive")),
                    bundle,
                    false);
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
