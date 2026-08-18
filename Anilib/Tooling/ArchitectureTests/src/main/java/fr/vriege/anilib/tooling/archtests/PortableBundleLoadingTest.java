package fr.vriege.anilib.tooling.archtests;

import fr.vriege.anilib.configuration.standard.StandardAnilib;
import fr.vriege.anilib.feature.extensionrepository.ExtensionArtifactFormat;
import fr.vriege.anilib.feature.extensionrepository.ExtensionInstallationState;
import fr.vriege.anilib.feature.extensionrepository.ExtensionRepositoryCapabilities;
import fr.vriege.anilib.feature.extensionrepository.InstalledExtensionPackage;
import fr.vriege.anilib.feature.extensionrepository.runtime.FileInstalledExtensionStore;
import fr.vriege.anilib.feature.source.SourceCapabilities;
import fr.vriege.anilib.feature.source.SourceId;
import fr.vriege.anilib.kernel.StartedAnilib;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

/** End-to-end checks for restart-isolated, explicitly selected portable source Bundles. */
final class PortableBundleLoadingTest {
    private static final String PACKAGE = "eu.example.extension";
    private static final String MODULE = "eu.example.extension";

    private PortableBundleLoadingTest() {
    }

    static int run() {
        Path directory = temporaryDirectory();
        try {
            byte[] bundle = compileBundle(directory.resolve("compiler"));
            InstalledExtensionPackage extension = installed(PACKAGE, bundle, ExtensionInstallationState.ENABLED);
            Path installationDirectory = directory.resolve("extensions");
            new FileInstalledExtensionStore(installationDirectory.resolve("installed.tsv"))
                    .save(Map.of(extension.packageName(), extension));
            writeArtifact(installationDirectory, extension, bundle);

            try (StartedAnilib application = StandardAnilib.start(directory)) {
                List<?> loadFailures = application.capability(ExtensionRepositoryCapabilities.INSTALLATION)
                        .loadFailures();
                check(application.capability(SourceCapabilities.REGISTRY)
                                .find(SourceId.of("example.remote"))
                                .isPresent(),
                        "Standard must register an enabled portable source Bundle after restart: " + loadFailures);
                check(loadFailures.isEmpty(),
                        "a valid explicit module must not report a startup load failure");
            }

            InstalledExtensionPackage disabled = installed(
                    PACKAGE,
                    bundle,
                    ExtensionInstallationState.DISABLED);
            new FileInstalledExtensionStore(installationDirectory.resolve("installed.tsv"))
                    .save(Map.of(disabled.packageName(), disabled));
            try (StartedAnilib application = StandardAnilib.start(directory)) {
                check(application.capability(SourceCapabilities.REGISTRY)
                                .find(SourceId.of("example.remote"))
                                .isEmpty(),
                        "a disabled portable Bundle must remain absent after restart");
                check(application.capability(ExtensionRepositoryCapabilities.INSTALLATION)
                                .loadFailures()
                                .isEmpty(),
                        "a disabled portable Bundle must be skipped without a load failure");
            }

            InstalledExtensionPackage broken = installed(
                    "eu.example.broken",
                    "missing".getBytes(StandardCharsets.UTF_8),
                    ExtensionInstallationState.ENABLED);
            Map<String, InstalledExtensionPackage> withBroken = new LinkedHashMap<>();
            withBroken.put(extension.packageName(), extension);
            withBroken.put(broken.packageName(), broken);
            new FileInstalledExtensionStore(installationDirectory.resolve("installed.tsv")).save(withBroken);
            try (StartedAnilib application = StandardAnilib.start(directory)) {
                check(application.capability(SourceCapabilities.REGISTRY)
                                .find(SourceId.of("example.remote"))
                                .isPresent(),
                        "one broken installed artifact must not prevent valid Bundles from starting");
                check(application.capability(ExtensionRepositoryCapabilities.INSTALLATION)
                                .loadFailures()
                                .size() == 1,
                        "a broken enabled Bundle must be exposed as one isolated startup failure");
            }
            return 6;
        } finally {
            deleteDirectory(directory);
        }
    }

    private static byte[] compileBundle(Path compilerDirectory) {
        Path sources = compilerDirectory.resolve("sources");
        Path classes = compilerDirectory.resolve("classes");
        try {
            Path packageDirectory = sources.resolve("eu/example/extension");
            Files.createDirectories(packageDirectory);
            Files.writeString(sources.resolve("module-info.java"), """
                    module eu.example.extension {
                        requires fr.vriege.anilib.feature.source.api;
                        exports eu.example.extension;
                    }
                    """, StandardCharsets.UTF_8);
            Files.writeString(packageDirectory.resolve("ExampleFactory.java"), """
                    package eu.example.extension;

                    import fr.vriege.anilib.feature.source.SourceApiVersion;
                    import fr.vriege.anilib.feature.source.SourceContentKind;
                    import fr.vriege.anilib.feature.source.SourceDescriptor;
                    import fr.vriege.anilib.feature.source.SourceExtensionContext;
                    import fr.vriege.anilib.feature.source.SourceExtensionFactory;
                    import fr.vriege.anilib.feature.source.SourceId;
                    import java.util.Set;

                    public final class ExampleFactory implements SourceExtensionFactory {
                        public ExampleFactory() {
                        }

                        @Override
                        public fr.vriege.anilib.feature.source.Source create(SourceExtensionContext context) {
                            return () -> new SourceDescriptor(
                                    SourceId.of("example.remote"),
                                    "Example remote",
                                    "1.0",
                                    "en",
                                    Set.of(SourceContentKind.MANGA),
                                    new SourceApiVersion(1, 4));
                        }
                    }
                    """, StandardCharsets.UTF_8);
            Files.createDirectories(classes);
            JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
            String modulePath = System.getProperty("jdk.module.path");
            int exitCode = compiler.run(
                    null,
                    null,
                    null,
                    "--module-path",
                    modulePath,
                    "-d",
                    classes.toString(),
                    sources.resolve("module-info.java").toString(),
                    packageDirectory.resolve("ExampleFactory.java").toString());
            if (exitCode != 0) {
                throw new AssertionError("Unable to compile the portable Bundle fixture");
            }
            return jar(classes, compilerDirectory.resolve("example.jar"));
        } catch (IOException exception) {
            throw new AssertionError("Unable to create the portable Bundle fixture", exception);
        }
    }

    private static byte[] jar(Path classes, Path destination) throws IOException {
        try (JarOutputStream archive = new JarOutputStream(Files.newOutputStream(destination));
             java.util.stream.Stream<Path> files = Files.walk(classes)) {
            for (Path file : files.filter(Files::isRegularFile).sorted().toList()) {
                String name = classes.relativize(file).toString().replace('\\', '/');
                archive.putNextEntry(new JarEntry(name));
                Files.copy(file, archive);
                archive.closeEntry();
            }
            archive.putNextEntry(new JarEntry("META-INF/anilib-extension.properties"));
            archive.write(("""
                    package=%s
                    versionCode=1
                    api=1.4
                    module=%s
                    source.count=1
                    source.0.id=example.remote
                    source.0.component=extension.example.remote
                    source.0.name=Example remote
                    source.0.factory=eu.example.extension.ExampleFactory
                    source.0.origins=https://example.org
                    """).formatted(PACKAGE, MODULE).getBytes(StandardCharsets.UTF_8));
            archive.closeEntry();
        }
        return Files.readAllBytes(destination);
    }

    private static InstalledExtensionPackage installed(
            String packageName,
            byte[] bundle,
            ExtensionInstallationState state) {
        return new InstalledExtensionPackage(
                packageName,
                packageName,
                1,
                "1.0",
                ExtensionArtifactFormat.ANILIB_BUNDLE,
                state,
                sha256(bundle),
                Instant.parse("2026-08-18T12:00:00Z"));
    }

    private static void writeArtifact(
            Path installationDirectory,
            InstalledExtensionPackage extension,
            byte[] bytes) {
        Path artifact = installationDirectory.resolve("artifacts").resolve(
                packageHash(extension.packageName()) + "-" + extension.versionCode()
                        + "-" + extension.sha256().substring(0, 16) + ".jar");
        try {
            Files.createDirectories(artifact.getParent());
            Files.write(artifact, bytes);
        } catch (IOException exception) {
            throw new AssertionError("Unable to store the portable Bundle fixture", exception);
        }
    }

    private static String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (GeneralSecurityException exception) {
            throw new AssertionError("JDK must provide SHA-256", exception);
        }
    }

    private static String packageHash(String packageName) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(packageName.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 16);
        } catch (GeneralSecurityException exception) {
            throw new AssertionError("JDK must provide SHA-256", exception);
        }
    }

    private static Path temporaryDirectory() {
        try {
            return Files.createTempDirectory("anilib-portable-bundle-test");
        } catch (IOException exception) {
            throw new AssertionError("Unable to create portable Bundle test directory", exception);
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
            throw new AssertionError("Unable to clean portable Bundle test directory", exception);
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
