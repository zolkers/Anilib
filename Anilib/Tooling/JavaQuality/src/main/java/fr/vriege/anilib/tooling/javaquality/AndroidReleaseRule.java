package fr.vriege.anilib.tooling.javaquality;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Keeps Android packaging, signing, and platform confinement explicit. */
public final class AndroidReleaseRule implements AnilibJavaRule {
    private static final Path ANDROID_BUILD = Path.of("Anilib", "Platforms", "Android", "build.gradle");
    private static final Path ANDROID_MODULE = Path.of("Anilib", "Platforms", "Android", "module.properties");
    private static final Path MANIFEST = Path.of(
            "Anilib", "Platforms", "Android", "src", "main", "AndroidManifest.xml");
    private static final Path WORKFLOW = Path.of(".github", "workflows", "android-release.yml");

    public AndroidReleaseRule() {
    }

    @Override
    public String name() {
        return "android-release";
    }

    @Override
    public List<Diagnostic> analyze(RepositorySnapshot repository) {
        List<Diagnostic> diagnostics = new ArrayList<>();
        requireTokens(
                repository,
                ANDROID_BUILD,
                diagnostics,
                "compileSdk = 37",
                "minSdk = 26",
                "targetSdk = 37",
                "anilibAndroidVersionName",
                "anilibAndroidVersionCode",
                "ANILIB_ANDROID_KEYSTORE",
                "ANILIB_ANDROID_KEYSTORE_PASSWORD",
                "ANILIB_ANDROID_KEY_ALIAS",
                "ANILIB_ANDROID_KEY_PASSWORD",
                "writeAndroidReleaseChecksums",
                "MessageDigest.getInstance('SHA-256')");
        requireTokens(
                repository,
                ANDROID_MODULE,
                diagnostics,
                "layer=PLATFORM",
                "language=KOTLIN",
                "externalDependencies=androidx.activity:activity-compose");
        requireTokens(
                repository,
                MANIFEST,
                diagnostics,
                "android:usesCleartextTraffic=\"false\"",
                "android:exported=\"false\"");
        requireTokens(
                repository,
                WORKFLOW,
                diagnostics,
                "ubuntu-24.04",
                "actions/checkout@v6.0.2",
                "actions/setup-java@v5.6.0",
                "java-version: 21.0.10",
                "sdkmanager --install \"platforms;android-37\"",
                "writeAndroidReleaseChecksums",
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
                    "Required Android release file is missing or symbolic"));
            return;
        }
        try {
            String content = Files.readString(file, StandardCharsets.UTF_8);
            for (String token : requiredTokens) {
                if (!content.contains(token)) {
                    diagnostics.add(new Diagnostic(name(), relativePath, 1,
                            "Android release contract is missing: " + token));
                }
            }
        } catch (IOException exception) {
            throw new UncheckedIOException("Unable to inspect " + file, exception);
        }
    }
}
