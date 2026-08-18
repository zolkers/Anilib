package fr.vriege.anilib.tooling.javaquality;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class ApplicationReleaseRule implements AnilibJavaRule {
    private static final Path APPLICATION_WORKFLOW =
            Path.of(".github", "workflows", "application-release.yml");
    private static final Path DESKTOP_WORKFLOW =
            Path.of(".github", "workflows", "desktop-release.yml");
    private static final Path ANDROID_WORKFLOW =
            Path.of(".github", "workflows", "android-release.yml");

    public ApplicationReleaseRule() {
    }

    @Override
    public String name() {
        return "application-release";
    }

    @Override
    public List<Diagnostic> analyze(RepositorySnapshot repository) {
        List<Diagnostic> diagnostics = new ArrayList<>();
        requireTokens(
                repository,
                APPLICATION_WORKFLOW,
                diagnostics,
                "v[0-9]+.[0-9]+.[0-9]+",
                "uses: ./.github/workflows/desktop-release.yml",
                "uses: ./.github/workflows/android-release.yml",
                "require-signing: true",
                "actions/download-artifact@v8.0.1",
                "sha256sum --check SHA256SUMS",
                "actions/attest@v4",
                "attestations: write",
                "gh release create");
        requireTokens(
                repository,
                DESKTOP_WORKFLOW,
                diagnostics,
                "workflow_call:",
                "ANILIB_WINDOWS_CERTIFICATE_BASE64",
                "signtool.exe",
                "notarizeDmg",
                "compose.desktop.mac.sign=true",
                "xcrun stapler validate");
        requireTokens(
                repository,
                ANDROID_WORKFLOW,
                diagnostics,
                "workflow_call:",
                "Validate production signing secrets",
                "*-unsigned.apk",
                "apksigner",
                "--print-certs");
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
                    "Required application release file is missing or symbolic"));
            return;
        }
        try {
            String content = Files.readString(file, StandardCharsets.UTF_8);
            for (String token : requiredTokens) {
                if (!content.contains(token)) {
                    diagnostics.add(new Diagnostic(name(), relativePath, 1,
                            "Application release contract is missing: " + token));
                }
            }
        } catch (IOException exception) {
            throw new UncheckedIOException("Unable to inspect " + file, exception);
        }
    }
}
