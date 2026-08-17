package fr.vriege.anilib.feature.discovery;

import fr.vriege.anilib.kernel.CapabilityKey;

/** Stable behavior capabilities published by the Discovery Bundle. */
public final class DiscoveryCapabilities {
    public static final CapabilityKey<DiscoveryService> SERVICE =
            CapabilityKey.of("feature.discovery.service", DiscoveryService.class);

    private DiscoveryCapabilities() {
    }
}
