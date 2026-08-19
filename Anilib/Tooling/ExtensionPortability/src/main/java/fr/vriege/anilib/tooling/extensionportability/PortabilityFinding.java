package fr.vriege.anilib.tooling.extensionportability;

import java.nio.file.Path;
import java.util.Objects;

public record PortabilityFinding(
        String category,
        PortabilitySeverity severity,
        Path path,
        int line,
        String detail) {
    public PortabilityFinding {
        category = requireText(category, "category");
        severity = Objects.requireNonNull(severity, "severity must not be null");
        path = Objects.requireNonNull(path, "path must not be null");
        if (path.isAbsolute()) {
            throw new IllegalArgumentException("finding path must be relative");
        }
        if (line < 1) {
            throw new IllegalArgumentException("line must be positive");
        }
        detail = requireText(detail, "detail");
    }

    private static String requireText(String value, String name) {
        String text = Objects.requireNonNull(value, name + " must not be null").strip();
        if (text.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return text;
    }
}
