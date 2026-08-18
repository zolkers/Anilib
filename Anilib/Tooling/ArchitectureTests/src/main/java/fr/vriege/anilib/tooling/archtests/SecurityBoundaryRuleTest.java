package fr.vriege.anilib.tooling.archtests;

import fr.vriege.anilib.tooling.javaquality.RepositorySnapshot;
import fr.vriege.anilib.tooling.javaquality.SecurityBoundaryRule;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

final class SecurityBoundaryRuleTest {
    private SecurityBoundaryRuleTest() {
    }

    static int run() {
        Path repository = temporaryDirectory();
        try {
            write(repository, "Anilib/Features/ExtensionRepository/Runtime/src/main/java/fr/vriege/anilib/feature/"
                    + "extensionrepository/runtime/InMemoryModuleArchive.java",
                    "MAX_ENTRY_BYTES MAX_EXPANDED_BYTES MAX_ENTRIES name.contains(\"../\") putIfAbsent");
            write(repository, "Anilib/Features/LocalSource/Runtime/src/main/java/fr/vriege/anilib/feature/"
                    + "localsource/runtime/FileSystemLocalContentSource.java",
                    "MAX_PAGE_BYTES MAX_ARCHIVE_ENTRIES Files.isSymbolicLink isSafeEntry");
            write(repository, "Anilib/Features/Backup/Runtime/src/main/java/fr/vriege/anilib/feature/backup/"
                    + "runtime/BackupArchiveStore.java",
                    "MAXIMUM_ARCHIVE_BYTES MAXIMUM_SECTIONS MAXIMUM_SECTION_BYTES Files.isSymbolicLink "
                            + "MessageDigest.isEqual");
            write(repository, "Anilib/Framework/Http/Runtime/src/main/java/fr/vriege/anilib/framework/http/"
                    + "runtime/MediaHeaderProxy.java",
                    "127.0.0.1 MAXIMUM_REQUEST_BYTES MAXIMUM_PLAYLIST_BYTES BLOCKED_REQUEST_HEADERS "
                            + "FORWARDED_RESPONSE_HEADERS SecureRandom");
            write(repository, "Anilib/Features/ApplicationUpdate/Runtime/src/main/java/fr/vriege/anilib/feature/"
                    + "applicationupdate/runtime/ApplicationReleaseManifest.java",
                    "MAX_MANIFEST_BYTES Ed25519 anilib-update-v1 zolkers/Anilib "
                            + ".github/workflows/application-release.yml");
            Path review = write(repository, "Anilib/SECURITY_REVIEW.md",
                    "Extension repositories Loopback media relay Backup import Application updater "
                            + "Release supply chain");
            SecurityBoundaryRule rule = new SecurityBoundaryRule();
            RepositorySnapshot snapshot = new RepositorySnapshot(
                    repository, List.of(), List.of(), List.of(), List.of());
            check(rule.analyze(snapshot).isEmpty(), "complete security boundary contracts must pass");
            Files.writeString(review, "Release supply chain", StandardCharsets.UTF_8);
            check(rule.analyze(snapshot).stream().anyMatch(diagnostic ->
                            diagnostic.message().contains("Application updater")),
                    "removing a reviewed security boundary must fail closed");
            return 2;
        } catch (IOException exception) {
            throw new AssertionError("Unable to test security boundary rule", exception);
        } finally {
            deleteDirectory(repository);
        }
    }

    private static Path write(Path root, String relative, String content) throws IOException {
        Path file = root.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content, StandardCharsets.UTF_8);
        return file;
    }

    private static Path temporaryDirectory() {
        try {
            return Files.createTempDirectory("anilib-security-boundary-rule");
        } catch (IOException exception) {
            throw new AssertionError("Unable to create security rule fixture", exception);
        }
    }

    private static void deleteDirectory(Path directory) {
        try (var entries = Files.walk(directory)) {
            for (Path entry : entries.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(entry);
            }
        } catch (IOException exception) {
            throw new AssertionError("Unable to clean security rule fixture", exception);
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
