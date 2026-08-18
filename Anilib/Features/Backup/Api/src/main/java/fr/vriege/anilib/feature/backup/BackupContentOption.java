package fr.vriege.anilib.feature.backup;

import fr.vriege.anilib.foundation.validation.Preconditions;
import fr.vriege.anilib.framework.backup.BackupSectionId;

import java.util.Objects;

public record BackupContentOption(
        BackupSectionId id,
        String displayName,
        long entryCount) {
    public BackupContentOption {
        Objects.requireNonNull(id, "id must not be null");
        Preconditions.requireNonBlank(displayName, "displayName");
        if (entryCount < 0) {
            throw new IllegalArgumentException("entryCount must not be negative");
        }
    }
}
