package fr.vriege.anilib.feature.backup;

import fr.vriege.anilib.framework.backup.BackupSectionId;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Set;

public record BackupPolicy(
        BackupSchedule schedule,
        int retentionCount,
        Set<BackupSectionId> includedSections,
        Path destination) {
    public BackupPolicy {
        Objects.requireNonNull(schedule, "schedule must not be null");
        if (retentionCount < 1 || retentionCount > 100) {
            throw new IllegalArgumentException("retentionCount must be between 1 and 100");
        }
        includedSections = Set.copyOf(includedSections);
        if (includedSections.isEmpty()) {
            throw new IllegalArgumentException("includedSections must not be empty");
        }
        Path normalized = Objects.requireNonNull(destination, "destination must not be null")
                .toAbsolutePath()
                .normalize();
        if (normalized.getParent() == null) {
            throw new IllegalArgumentException("destination must not be a filesystem root");
        }
        destination = normalized;
    }

    public BackupPolicy withSchedule(BackupSchedule value) {
        return new BackupPolicy(value, retentionCount, includedSections, destination);
    }

    public BackupPolicy withRetentionCount(int value) {
        return new BackupPolicy(schedule, value, includedSections, destination);
    }

    public BackupPolicy withIncludedSections(Set<BackupSectionId> value) {
        return new BackupPolicy(schedule, retentionCount, value, destination);
    }

    public BackupPolicy withDestination(Path value) {
        return new BackupPolicy(schedule, retentionCount, includedSections, value);
    }
}
