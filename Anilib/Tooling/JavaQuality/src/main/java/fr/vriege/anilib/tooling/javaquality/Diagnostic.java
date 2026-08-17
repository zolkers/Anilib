package fr.vriege.anilib.tooling.javaquality;

import java.nio.file.Path;
import java.util.Objects;

/** One deterministic repository-relative quality finding. */
public record Diagnostic(String rule, Path path, int line, String message) implements Comparable<Diagnostic> {
    public Diagnostic {
        Objects.requireNonNull(rule, "rule must not be null");
        Objects.requireNonNull(path, "path must not be null");
        Objects.requireNonNull(message, "message must not be null");
        if (line < 1) {
            throw new IllegalArgumentException("line must be positive");
        }
    }

    @Override
    public int compareTo(Diagnostic other) {
        int pathComparison = path.toString().compareTo(other.path.toString());
        if (pathComparison != 0) {
            return pathComparison;
        }
        int lineComparison = Integer.compare(line, other.line);
        return lineComparison != 0 ? lineComparison : rule.compareTo(other.rule);
    }
}
