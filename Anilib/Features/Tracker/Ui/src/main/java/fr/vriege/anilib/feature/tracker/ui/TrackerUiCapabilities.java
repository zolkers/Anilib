package fr.vriege.anilib.feature.tracker.ui;

import fr.vriege.anilib.kernel.CapabilityKey;

/** Presentation capability exported by the Tracker Bundle. */
public final class TrackerUiCapabilities {
    public static final CapabilityKey<TrackerPresentation> PRESENTATION =
            CapabilityKey.of("feature.tracker.ui.presentation", TrackerPresentation.class);

    private TrackerUiCapabilities() {
    }
}
