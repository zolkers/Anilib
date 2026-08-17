package fr.vriege.anilib.feature.localsource;

import fr.vriege.anilib.kernel.CapabilityKey;

/** Stable capabilities published by the Local Source Bundle. */
public final class LocalSourceCapabilities {
    public static final CapabilityKey<LocalContentSource> CONTENT =
            CapabilityKey.of("feature.local-source.content", LocalContentSource.class);

    private LocalSourceCapabilities() {
    }
}
