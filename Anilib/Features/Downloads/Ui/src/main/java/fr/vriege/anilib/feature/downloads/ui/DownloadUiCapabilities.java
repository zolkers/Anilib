package fr.vriege.anilib.feature.downloads.ui;

import fr.vriege.anilib.kernel.CapabilityKey;

/** Stable shared Downloads presentation capability for Android and desktop. */
public final class DownloadUiCapabilities {
    public static final CapabilityKey<DownloadPresentation> PRESENTATION =
            CapabilityKey.of("feature.downloads.presentation", DownloadPresentation.class);

    private DownloadUiCapabilities() {
    }
}
