package fr.vriege.anilib.feature.backup;

import fr.vriege.anilib.foundation.validation.Preconditions;

import java.time.Instant;
import java.util.List;

/** Successful transaction result listing only sections restored by this product. */
public record BackupRestoreResult(
        Instant restoredAt,
        List<BackupSectionSnapshot> restoredSections) {
    public BackupRestoreResult {
        Preconditions.requireNonNull(restoredAt, "restoredAt");
        restoredSections = List.copyOf(restoredSections);
        if (restoredSections.stream().anyMatch(section -> !section.restorable())) {
            throw new IllegalArgumentException("restoredSections must contain only restorable sections");
        }
    }

    public int entryCount() {
        return restoredSections.stream().mapToInt(BackupSectionSnapshot::entryCount).sum();
    }
}
