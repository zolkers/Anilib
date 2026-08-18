package fr.vriege.anilib.framework.backup;

import fr.vriege.anilib.foundation.validation.Preconditions;

/** Stable identifier for one independently versioned feature-owned backup section. */
public record BackupSectionId(String value) implements Comparable<BackupSectionId> {
    public BackupSectionId {
        value = Preconditions.requireNonBlank(value, "value");
        if (!value.matches("[a-z][a-z0-9.-]*")) {
            throw new IllegalArgumentException("value must be a stable lowercase identifier");
        }
    }

    public static BackupSectionId of(String value) {
        return new BackupSectionId(value);
    }

    @Override
    public int compareTo(BackupSectionId other) {
        return value.compareTo(other.value);
    }

    @Override
    public String toString() {
        return value;
    }
}
