package fr.vriege.anilib.feature.backup;

import fr.vriege.anilib.foundation.validation.Preconditions;

import java.nio.file.Path;
import java.time.Instant;

/** Lightweight local backup row for the shared management screen. */
public record BackupFileSnapshot(
        Path path,
        Instant createdAt,
        long sizeBytes,
        int sectionCount,
        int entryCount) {
    public BackupFileSnapshot {
        path = Preconditions.requireNonNull(path, "path").toAbsolutePath().normalize();
        Preconditions.requireNonNull(createdAt, "createdAt");
        if (sizeBytes < 0 || sectionCount < 0 || entryCount < 0) {
            throw new IllegalArgumentException("backup counts and size must not be negative");
        }
    }
}
