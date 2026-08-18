package fr.vriege.anilib.feature.network.bundle;

import fr.vriege.anilib.feature.network.NetworkMaintenance;
import fr.vriege.anilib.framework.http.HttpCookieJar;
import fr.vriege.anilib.framework.http.HttpResponseCache;
import fr.vriege.anilib.foundation.validation.Preconditions;

/** Direct maintenance adapter over the Bundle-owned cookie jar and response cache. */
final class DefaultNetworkMaintenance implements NetworkMaintenance {
    private final HttpCookieJar cookies;
    private final HttpResponseCache cache;

    DefaultNetworkMaintenance(HttpCookieJar cookies, HttpResponseCache cache) {
        this.cookies = Preconditions.requireNonNull(cookies, "cookies");
        this.cache = Preconditions.requireNonNull(cache, "cache");
    }

    @Override
    public void clearCookies() {
        cookies.clear();
    }

    @Override
    public void clearResponseCache() {
        cache.clear();
    }
}
