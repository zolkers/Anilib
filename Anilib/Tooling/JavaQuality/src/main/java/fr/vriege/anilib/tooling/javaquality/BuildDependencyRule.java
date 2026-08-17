package fr.vriege.anilib.tooling.javaquality;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Rejects third-party libraries and build plugins. */
public final class BuildDependencyRule implements AnilibJavaRule {
    private static final Pattern DEPENDENCY = Pattern.compile(
            "^\\s*(?:api|implementation|compileOnly|runtimeOnly|testImplementation|testRuntimeOnly)\\s+(.+)$");
    private static final Pattern PLUGIN = Pattern.compile("^\\s*id\\s+['\"]([^'\"]+)['\"].*$");
    private static final Set<String> ALLOWED_PLUGINS = Set.of("application", "base", "java", "java-library");

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
        if (dependency.matches() && !dependency.group(1).startsWith("project(")) {
            diagnostics.add(new Diagnostic(name(), path, lineNumber,
                    "Only Anilib project dependencies are allowed"));
        }
        Matcher plugin = PLUGIN.matcher(line);
        if (plugin.matches() && !ALLOWED_PLUGINS.contains(plugin.group(1))) {
            diagnostics.add(new Diagnostic(name(), path, lineNumber,
                    "External build plugin is forbidden: " + plugin.group(1)));
        }
    }
}
