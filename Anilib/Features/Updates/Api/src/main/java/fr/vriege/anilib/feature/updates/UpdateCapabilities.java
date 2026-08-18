package fr.vriege.anilib.feature.updates;

import fr.vriege.anilib.framework.backup.BackupSectionCodec;
import fr.vriege.anilib.kernel.CapabilityKey;

public final class UpdateCapabilities {
    public static final CapabilityKey<LibraryUpdateService> SERVICE =
            CapabilityKey.of("feature.updates.service", LibraryUpdateService.class);
    public static final CapabilityKey<LibraryUpdateNotifier> NOTIFIER =
            CapabilityKey.of("feature.updates.notifier", LibraryUpdateNotifier.class);
    public static final CapabilityKey<BackupSectionCodec> BACKUP_CODEC =
            CapabilityKey.of("feature.updates.backup-codec", BackupSectionCodec.class);

    private UpdateCapabilities() {
    }
}
