package fr.vriege.anilib.tooling.archtests;

import fr.vriege.anilib.tooling.javaquality.ApplicationReleaseRule;
import fr.vriege.anilib.tooling.javaquality.RepositorySnapshot;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

final class ApplicationReleaseRuleTest {
    private ApplicationReleaseRuleTest() {
    }

    static int run() {
        Path repository = temporaryDirectory();
        try {
            Path application = repository.resolve(".github/workflows/application-release.yml");
            write(application, """
                    v[0-9]+.[0-9]+.[0-9]+
                    uses: ./.github/workflows/desktop-release.yml
                    uses: ./.github/workflows/android-release.yml
                    require-signing: true
                    actions/download-artifact@v8.0.1
                    sha256sum --check SHA256SUMS
                    actions/attest@v4
                    attestations: write
                    gh release create
                    """);
            write(repository.resolve(".github/workflows/desktop-release.yml"), """
                    workflow_call:
                    ANILIB_WINDOWS_CERTIFICATE_BASE64 signtool.exe notarizeDmg
                    compose.desktop.mac.sign=true xcrun stapler validate
                    """);
            write(repository.resolve(".github/workflows/android-release.yml"), """
                    workflow_call: Validate production signing secrets
                    *-unsigned.apk apksigner --print-certs
                    """);
            ApplicationReleaseRule rule = new ApplicationReleaseRule();
            RepositorySnapshot snapshot = snapshot(repository);
            check(rule.analyze(snapshot).isEmpty(),
                    "a signed multi-platform publication contract must pass");
            Files.writeString(application, "gh release create", StandardCharsets.UTF_8);
            check(rule.analyze(snapshot).stream()
                            .anyMatch(diagnostic -> diagnostic.message().contains("actions/attest@v4")),
                    "missing provenance signing must produce an actionable diagnostic");
            return 2;
        } catch (IOException exception) {
            throw new AssertionError("Unable to run application release rule test", exception);
        } finally {
            deleteDirectory(repository);
        }
    }

    private static RepositorySnapshot snapshot(Path root) {
        return new RepositorySnapshot(root, List.of(), List.of(), List.of(), List.of());
    }

    private static Path temporaryDirectory() {
        try {
            return Files.createTempDirectory("anilib-application-release-rule");
        } catch (IOException exception) {
            throw new AssertionError("Unable to create application release rule directory", exception);
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
            for (Path entry : entries.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(entry);
            }
        } catch (IOException exception) {
            throw new AssertionError("Unable to clean application release rule directory", exception);
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
