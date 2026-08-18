package fr.vriege.anilib.feature.discovery;

import fr.vriege.anilib.framework.backup.BackupSectionCodec;
import fr.vriege.anilib.kernel.CapabilityKey;

/** Stable behavior capabilities published by the Discovery Bundle. */
public final class DiscoveryCapabilities {
    public static final CapabilityKey<DiscoveryService> SERVICE =
            CapabilityKey.of("feature.discovery.service", DiscoveryService.class);
    public static final CapabilityKey<BackupSectionCodec> BACKUP_CODEC =
            CapabilityKey.of("feature.discovery.backup-codec", BackupSectionCodec.class);

    private DiscoveryCapabilities() {
    }
}
