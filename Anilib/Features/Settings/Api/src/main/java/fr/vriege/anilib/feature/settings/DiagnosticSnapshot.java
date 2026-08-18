package fr.vriege.anilib.feature.settings;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record DiagnosticSnapshot(
        Instant inspectedAt,
        Path dataDirectory,
        long totalBytes,
        long totalFiles,
        List<StorageUsage> storage,
        List<DiagnosticReport> reports) {
    public DiagnosticSnapshot {
        Objects.requireNonNull(inspectedAt, "inspectedAt must not be null");
        dataDirectory = Objects.requireNonNull(dataDirectory, "dataDirectory must not be null")
                .toAbsolutePath().normalize();
        storage = List.copyOf(storage);
        reports = List.copyOf(reports);
        if (totalBytes < 0 || totalFiles < 0) {
            throw new IllegalArgumentException("diagnostic totals must not be negative");
        }
    }
}
