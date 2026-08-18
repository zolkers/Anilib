package fr.vriege.anilib.feature.backup;

import fr.vriege.anilib.foundation.validation.Preconditions;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

/** Fully checksum-validated archive preview before any state is mutated. */
public record BackupInspection(
        Path path,
        Instant createdAt,
        long sizeBytes,
        List<BackupSectionSnapshot> sections) {
    public BackupInspection {
        path = Preconditions.requireNonNull(path, "path").toAbsolutePath().normalize();
        Preconditions.requireNonNull(createdAt, "createdAt");
        if (sizeBytes < 0) {
            throw new IllegalArgumentException("sizeBytes must not be negative");
        }
        sections = List.copyOf(sections);
    }

    public int entryCount() {
        return sections.stream().mapToInt(BackupSectionSnapshot::entryCount).sum();
    }
}
