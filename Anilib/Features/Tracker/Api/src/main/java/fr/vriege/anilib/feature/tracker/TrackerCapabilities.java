package fr.vriege.anilib.feature.tracker;

import fr.vriege.anilib.framework.backup.BackupSectionCodec;
import fr.vriege.anilib.kernel.CapabilityKey;

/** Typed capabilities published by the removable Tracker Bundle. */
public final class TrackerCapabilities {
    public static final CapabilityKey<TrackerRegistry> REGISTRY =
            CapabilityKey.of("feature.tracker.registry", TrackerRegistry.class);
    public static final CapabilityKey<TrackerRegistrar> REGISTRAR =
            CapabilityKey.of("feature.tracker.registrar", TrackerRegistrar.class);
    public static final CapabilityKey<TrackerService> SERVICE =
            CapabilityKey.of("feature.tracker.service", TrackerService.class);
    public static final CapabilityKey<BackupSectionCodec> BACKUP_CODEC =
            CapabilityKey.of("feature.tracker.backup-codec", BackupSectionCodec.class);

    private TrackerCapabilities() {
    }
}
