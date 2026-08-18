package fr.vriege.anilib.feature.downloads;

import fr.vriege.anilib.kernel.CapabilityKey;

public final class DownloadCapabilities {
    public static final CapabilityKey<DownloadService> SERVICE =
            CapabilityKey.of("feature.downloads.service", DownloadService.class);

    private DownloadCapabilities() {
    }
}
