package fr.vriege.anilib.feature.player;

import fr.vriege.anilib.framework.backup.BackupSectionCodec;
import fr.vriege.anilib.kernel.CapabilityKey;

/** Stable Player capabilities published by its Bundle. */
public final class PlayerCapabilities {
    public static final CapabilityKey<PlayerService> SERVICE =
            CapabilityKey.of("feature.player.service", PlayerService.class);
    public static final CapabilityKey<BackupSectionCodec> BACKUP_CODEC =
            CapabilityKey.of("feature.player.backup-codec", BackupSectionCodec.class);

    private PlayerCapabilities() {
    }
}
