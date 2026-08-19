package fr.vriege.anilib.tooling.archtests;

import fr.vriege.anilib.tooling.javaquality.DesktopReleaseRule;
import fr.vriege.anilib.tooling.javaquality.RepositorySnapshot;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

final class DesktopReleaseRuleTest {
    private DesktopReleaseRuleTest() {
    }

    static int run() {
        Path repository = temporaryDirectory();
        try {
            write(repository.resolve("build.gradle"), """
                    preserveFileTimestamps = false
                    reproducibleFileOrder = true
                    failOnDynamicVersions()
                    failOnChangingVersions()
                    """);
            write(repository.resolve("Anilib/Platforms/Desktop/build.gradle"), """
                    TargetFormat.Dmg TargetFormat.Msi TargetFormat.Deb anilibPackageVersion
                    upgradeUuid = bundleID = 'fr.vriege.anilib'
                    licenseFile.set(rootProject.file('LICENSE'))
                    writeDesktopReleaseChecksums MessageDigest.getInstance('SHA-256')
                    """);
            Path engine = repository.resolve(
                    "Anilib/Platforms/Desktop/src/main/java/fr/vriege/anilib/platform/desktop/"
                            + "DesktopExtensionEngineInstaller.java");
            write(engine, """
                    miwayomi/miwayomi/releases/download/v0.2.9/miwayomi-all.jar
                    475b95dabaaca9f283263a5eafa12bec3580b658caae70407ae227ae4fa0e9b7
                    SIZE_BYTES = 102_140_165L HttpClient.Redirect.NORMAL
                    Files.createTempFile StandardCopyOption.ATOMIC_MOVE
                    """);
            Path workflow = repository.resolve(".github/workflows/desktop-release.yml");
            write(workflow, """
                    windows-2025 ubuntu-24.04 macos-15
                    actions/checkout@v6.0.2 actions/setup-java@v5.6.0
                    java-version: 21.0.10 writeDesktopReleaseChecksums
                    actions/upload-artifact@v7.0.1
                    """);
            DesktopReleaseRule rule = new DesktopReleaseRule();
            RepositorySnapshot snapshot = snapshot(repository);
            check(rule.analyze(snapshot).isEmpty(),
                    "a complete three-host desktop release contract must pass");
            Files.writeString(workflow, "windows-2025", StandardCharsets.UTF_8);
            check(rule.analyze(snapshot).stream()
                            .anyMatch(diagnostic -> diagnostic.message().contains("ubuntu-24.04")),
                    "a missing release host must produce an actionable diagnostic");
            Files.writeString(engine, "HttpClient.Redirect.NORMAL", StandardCharsets.UTF_8);
            check(rule.analyze(snapshot).stream()
                            .anyMatch(diagnostic -> diagnostic.message().contains(
                                    "475b95dabaaca9f283263a5eafa12bec3580b658caae70407ae227ae4fa0e9b7")),
                    "desktop APK compatibility must retain its pinned engine digest");
            return 3;
        } catch (IOException exception) {
            throw new AssertionError("Unable to run desktop release rule test", exception);
        } finally {
            deleteDirectory(repository);
        }
    }

    private static RepositorySnapshot snapshot(Path root) {
        return new RepositorySnapshot(root, List.of(), List.of(), List.of(), List.of());
    }

    private static Path temporaryDirectory() {
        try {
            return Files.createTempDirectory("anilib-desktop-release-rule");
        } catch (IOException exception) {
            throw new AssertionError("Unable to create desktop release rule directory", exception);
        }
    }

    private static void write(Path path, String content) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, content, StandardCharsets.UTF_8);
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
            throw new AssertionError("Unable to clean desktop release rule directory", exception);
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
