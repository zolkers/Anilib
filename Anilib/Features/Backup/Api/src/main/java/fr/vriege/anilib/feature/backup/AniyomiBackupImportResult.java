package fr.vriege.anilib.feature.backup;

import fr.vriege.anilib.foundation.validation.Preconditions;

import java.time.Instant;

public record AniyomiBackupImportResult(
        Instant importedAt,
        int createdCount,
        int updatedCount,
        AniyomiBackupInspection inspection) {
    public AniyomiBackupImportResult {
        Preconditions.requireNonNull(importedAt, "importedAt");
        if (createdCount < 0 || updatedCount < 0) {
            throw new IllegalArgumentException("import counts must not be negative");
        }
        Preconditions.requireNonNull(inspection, "inspection");
        if (Math.addExact(createdCount, updatedCount) != inspection.titleCount()) {
            throw new IllegalArgumentException("import counts must match inspected titles");
        }
    }

    public int importedCount() {
        return Math.addExact(createdCount, updatedCount);
    }
}
