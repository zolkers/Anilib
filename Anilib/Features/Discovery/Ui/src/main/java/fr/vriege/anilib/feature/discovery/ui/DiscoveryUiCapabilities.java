package fr.vriege.anilib.feature.discovery.ui;

import fr.vriege.anilib.kernel.CapabilityKey;

/** Stable shared presentation capability for Android and desktop renderers. */
public final class DiscoveryUiCapabilities {
    public static final CapabilityKey<DiscoveryPresentation> PRESENTATION =
            CapabilityKey.of("feature.discovery.presentation", DiscoveryPresentation.class);

    private DiscoveryUiCapabilities() {
    }
}
