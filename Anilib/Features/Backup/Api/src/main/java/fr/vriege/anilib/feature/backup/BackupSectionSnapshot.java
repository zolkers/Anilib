package fr.vriege.anilib.feature.backup;

import fr.vriege.anilib.foundation.validation.Preconditions;
import fr.vriege.anilib.framework.backup.BackupSectionId;

/** One inspected archive section, including whether this product can restore it. */
public record BackupSectionSnapshot(
        BackupSectionId id,
        String displayName,
        int version,
        int entryCount,
        boolean restorable) {
    public BackupSectionSnapshot {
        Preconditions.requireNonNull(id, "id");
        displayName = Preconditions.requireNonBlank(displayName, "displayName");
        if (version < 0 || entryCount < 0) {
            throw new IllegalArgumentException("version and entryCount must not be negative");
        }
    }
}
