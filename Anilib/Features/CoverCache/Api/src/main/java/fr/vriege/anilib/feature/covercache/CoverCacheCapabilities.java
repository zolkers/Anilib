package fr.vriege.anilib.feature.covercache;

import fr.vriege.anilib.kernel.CapabilityKey;

/** Stable capabilities published by the Cover Cache Bundle. */
public final class CoverCacheCapabilities {
    public static final CapabilityKey<CoverCache> CACHE =
            CapabilityKey.of("feature.cover-cache.cache", CoverCache.class);

    private CoverCacheCapabilities() {
    }
}
