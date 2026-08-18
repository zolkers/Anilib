package fr.vriege.anilib.tooling.javaquality;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class BuildDependencyRule implements AnilibJavaRule {
    private static final Pattern DEPENDENCY = Pattern.compile(
            "^\\s*(?:api|implementation|compileOnly|runtimeOnly|testImplementation|testRuntimeOnly)\\s+(.+)$");
    private static final Pattern PLUGIN = Pattern.compile(
            "^\\s*id\\s+['\"]([^'\"]+)['\"](?:\\s+version\\s+['\"]([^'\"]+)['\"])?(?:\\s+apply\\s+false)?\\s*$");
    private static final Pattern REPOSITORY = Pattern.compile(
            "^\\s*(google\\(\\)|mavenCentral\\(\\)|maven \\{ url = uri\\('([^']+)'\\) })\\s*$");
    private static final Set<String> ALLOWED_PLUGINS = Set.of("application", "base", "java", "java-library");
    private static final String ANDROID_BUILD = "Anilib/Platforms/Android/build.gradle";
    private static final String COMPOSE_BUILD = "Anilib/Platforms/Compose/build.gradle";
    private static final String DESKTOP_BUILD = "Anilib/Platforms/Desktop/build.gradle";
    private static final String ROOT_BUILD = "build.gradle";
    private static final Map<String, Set<String>> ALLOWED_EXTERNAL_DEPENDENCIES = Map.ofEntries(
            Map.entry(
                    ANDROID_BUILD,
                    Set.of(
                            "'androidx.activity:activity-compose:1.13.0'",
                            "'androidx.preference:preference-ktx:1.2.1'",
                            "'io.github.kevinnzou:compose-webview-multiplatform:2.0.3'")),
            Map.entry(
                    COMPOSE_BUILD,
                    Set.of(
                            "compose.foundation",
                            "compose.material3",
                            "compose.materialIconsExtended",
                            "'androidx.media3:media3-exoplayer-hls:1.10.1'",
                            "'io.github.kevinnzou:compose-webview-multiplatform:2.0.3'",
                            "'io.github.kdroidfilter:composemediaplayer:0.11.4'")),
            Map.entry(
                    DESKTOP_BUILD,
                    Set.of(
                            "compose.desktop.currentOs",
                            "'io.github.kevinnzou:compose-webview-multiplatform:2.0.3'")));
    private static final Map<String, Set<String>> ALLOWED_EXTERNAL_PLUGINS = Map.ofEntries(
            Map.entry(
                    ANDROID_BUILD,
                    Set.of(
                            "com.android.application@null",
                            "org.jetbrains.kotlin.plugin.compose@null")),
            Map.entry(
                    COMPOSE_BUILD,
                    Set.of(
                            "com.android.kotlin.multiplatform.library@null",
                            "org.jetbrains.compose@null",
                            "org.jetbrains.kotlin.multiplatform@null",
                            "org.jetbrains.kotlin.plugin.compose@null")),
            Map.entry(
                    DESKTOP_BUILD,
                    Set.of(
                            "org.jetbrains.kotlin.jvm@null",
                            "org.jetbrains.kotlin.plugin.compose@null",
                            "org.jetbrains.compose@null")),
            Map.entry(
                    ROOT_BUILD,
                    Set.of(
                            "com.android.application@9.1.1",
                            "com.android.kotlin.multiplatform.library@9.1.1",
                            "org.jetbrains.compose@1.11.1",
                            "org.jetbrains.kotlin.jvm@2.4.10",
                            "org.jetbrains.kotlin.multiplatform@2.4.10",
                            "org.jetbrains.kotlin.plugin.compose@2.4.10")));
    private static final Set<String> ALLOWED_REPOSITORY_BUILDS = Set.of(
            ANDROID_BUILD,
            COMPOSE_BUILD,
            DESKTOP_BUILD);

    public BuildDependencyRule() {
    }

    @Override
    public String name() {
        return "build-dependencies";
    }

    @Override
    public List<Diagnostic> analyze(RepositorySnapshot repository) {
        List<Diagnostic> diagnostics = new ArrayList<>();
        for (Path relativePath : repository.buildFiles()) {
            Path path = repository.root().resolve(relativePath);
            try {
                List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
                for (int index = 0; index < lines.size(); index++) {
                    validateLine(relativePath, index + 1, lines.get(index), diagnostics);
                }
            } catch (IOException exception) {
                throw new UncheckedIOException("Unable to read " + path, exception);
            }
        }
        return diagnostics;
    }

    private void validateLine(
            Path path,
            int lineNumber,
            String line,
            List<Diagnostic> diagnostics) {
        Matcher dependency = DEPENDENCY.matcher(line);
        String normalizedPath = path.toString().replace('\\', '/');
        if (dependency.matches()
                && !dependency.group(1).startsWith("project(")
                && !allowed(ALLOWED_EXTERNAL_DEPENDENCIES, normalizedPath, dependency.group(1))) {
            diagnostics.add(new Diagnostic(name(), path, lineNumber,
                    "Dependency is not in the platform UI allowlist"));
        }
        Matcher plugin = PLUGIN.matcher(line);
        if (plugin.matches()) {
            String pluginId = plugin.group(1);
            String pluginKey = pluginId + "@" + plugin.group(2);
            if (!ALLOWED_PLUGINS.contains(pluginId)
                    && !allowed(ALLOWED_EXTERNAL_PLUGINS, normalizedPath, pluginKey)) {
                diagnostics.add(new Diagnostic(name(), path, lineNumber,
                        "External build plugin or version is forbidden: " + pluginKey));
            }
        }
        Matcher repository = REPOSITORY.matcher(line);
        if (repository.matches()) {
            boolean allowedBuild = ALLOWED_REPOSITORY_BUILDS.contains(normalizedPath);
            String externalUrl = repository.group(2);
            boolean allowedExternal = externalUrl == null
                    || ((normalizedPath.equals(COMPOSE_BUILD) || normalizedPath.equals(DESKTOP_BUILD))
                    && externalUrl.equals("https://jogamp.org/deployment/maven"));
            if (!allowedBuild || !allowedExternal) {
                diagnostics.add(new Diagnostic(name(), path, lineNumber,
                        "Dependency repository is not in the exact platform UI allowlist"));
            }
        }
    }

    private static boolean allowed(Map<String, Set<String>> allowlist, String path, String value) {
        return allowlist.getOrDefault(path, Set.of()).contains(value);
    }
}
