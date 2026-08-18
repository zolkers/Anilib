package fr.vriege.anilib.feature.settings;

import java.time.Instant;
import java.util.Objects;

public record DiagnosticReport(
        DiagnosticReportType type,
        String name,
        Instant createdAt,
        long bytes) {
    public DiagnosticReport {
        Objects.requireNonNull(type, "type must not be null");
        name = Objects.requireNonNull(name, "name must not be null").strip();
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        if (name.isEmpty() || name.length() > 255 || bytes < 0) {
            throw new IllegalArgumentException("diagnostic report values are invalid");
        }
    }
}
