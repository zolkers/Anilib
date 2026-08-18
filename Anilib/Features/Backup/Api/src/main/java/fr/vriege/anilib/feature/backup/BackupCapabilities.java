package fr.vriege.anilib.feature.backup;

import fr.vriege.anilib.kernel.CapabilityKey;

/** Stable capabilities published by the removable Backup Bundle. */
public final class BackupCapabilities {
    public static final CapabilityKey<BackupService> SERVICE =
            CapabilityKey.of("feature.backup.service", BackupService.class);

    private BackupCapabilities() {
    }
}
