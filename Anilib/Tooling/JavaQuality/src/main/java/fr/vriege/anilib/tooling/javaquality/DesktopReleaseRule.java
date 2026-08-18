package fr.vriege.anilib.tooling.javaquality;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Keeps the three-host desktop packaging and reproducibility contract explicit. */
public final class DesktopReleaseRule implements AnilibJavaRule {
    private static final Path ROOT_BUILD = Path.of("build.gradle");
    private static final Path DESKTOP_BUILD = Path.of("Anilib", "Platforms", "Desktop", "build.gradle");
    private static final Path WORKFLOW = Path.of(".github", "workflows", "desktop-release.yml");

    public DesktopReleaseRule() {
    }

    @Override
    public String name() {
        return "desktop-release";
    }

    @Override
    public List<Diagnostic> analyze(RepositorySnapshot repository) {
        List<Diagnostic> diagnostics = new ArrayList<>();
        requireTokens(
                repository,
                ROOT_BUILD,
                diagnostics,
                "preserveFileTimestamps = false",
                "reproducibleFileOrder = true",
                "failOnDynamicVersions()",
                "failOnChangingVersions()");
        requireTokens(
                repository,
                DESKTOP_BUILD,
                diagnostics,
                "TargetFormat.Dmg",
                "TargetFormat.Msi",
                "TargetFormat.Deb",
                "anilibPackageVersion",
                "upgradeUuid =",
                "bundleID = 'fr.vriege.anilib'",
                "licenseFile.set(rootProject.file('LICENSE'))",
                "writeDesktopReleaseChecksums",
                "MessageDigest.getInstance('SHA-256')");
        requireTokens(
                repository,
                WORKFLOW,
                diagnostics,
                "windows-2025",
                "ubuntu-24.04",
                "macos-15",
                "actions/checkout@v6.0.2",
                "actions/setup-java@v5.6.0",
                "java-version: 21.0.10",
                "writeDesktopReleaseChecksums",
                "actions/upload-artifact@v7.0.1");
        return List.copyOf(diagnostics);
    }

    private void requireTokens(
            RepositorySnapshot repository,
            Path relativePath,
            List<Diagnostic> diagnostics,
            String... requiredTokens) {
        Path file = repository.root().resolve(relativePath);
        if (!Files.isRegularFile(file) || Files.isSymbolicLink(file)) {
            diagnostics.add(new Diagnostic(name(), relativePath, 1,
                    "Required desktop release file is missing or symbolic"));
            return;
        }
        try {
            String content = Files.readString(file, StandardCharsets.UTF_8);
            for (String token : requiredTokens) {
                if (!content.contains(token)) {
                    diagnostics.add(new Diagnostic(name(), relativePath, 1,
                            "Desktop release contract is missing: " + token));
                }
            }
        } catch (IOException exception) {
            throw new UncheckedIOException("Unable to inspect " + file, exception);
        }
    }
}
