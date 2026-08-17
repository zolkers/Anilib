package fr.vriege.anilib.feature.downloads;

import fr.vriege.anilib.kernel.CapabilityKey;

/** Stable Downloads capability published by its Bundle. */
public final class DownloadCapabilities {
    public static final CapabilityKey<DownloadService> SERVICE =
            CapabilityKey.of("feature.downloads.service", DownloadService.class);

    private DownloadCapabilities() {
    }
}
