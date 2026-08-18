package fr.vriege.anilib.framework.backup;

import fr.vriege.anilib.foundation.validation.Preconditions;

/** Validated feature-owned section metadata suitable for previews and restore reports. */
public record BackupSectionDetails(
        BackupSectionId id,
        String displayName,
        int version,
        int entryCount) {
    public BackupSectionDetails {
        Preconditions.requireNonNull(id, "id");
        displayName = Preconditions.requireNonBlank(displayName, "displayName");
        if (version < 0) {
            throw new IllegalArgumentException("version must not be negative");
        }
        if (entryCount < 0) {
            throw new IllegalArgumentException("entryCount must not be negative");
        }
    }
}
