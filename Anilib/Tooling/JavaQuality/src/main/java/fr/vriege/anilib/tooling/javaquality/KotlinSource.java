package fr.vriege.anilib.tooling.javaquality;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Indexed Kotlin source owned by one declared Anilib module. */
public record KotlinSource(
        Path path,
        Path absolutePath,
        ModuleMetadata module,
        List<String> lines) {
    private static final Pattern PACKAGE = Pattern.compile("^\\s*package\\s+([a-zA-Z0-9_.]+)\\s*$");

    public KotlinSource {
        Objects.requireNonNull(path, "path must not be null");
        Objects.requireNonNull(absolutePath, "absolutePath must not be null");
        Objects.requireNonNull(module, "module must not be null");
        lines = List.copyOf(lines);
    }

    public Optional<String> packageName() {
        for (String line : lines) {
            Matcher matcher = PACKAGE.matcher(line);
            if (matcher.matches()) {
                return Optional.of(matcher.group(1));
            }
        }
        return Optional.empty();
    }
}
