package fr.vriege.anilib.feature.network;

import fr.vriege.anilib.framework.http.AnilibHttpClient;
import fr.vriege.anilib.framework.http.HttpCookieJar;
import fr.vriege.anilib.framework.http.HttpRateLimiter;
import fr.vriege.anilib.framework.http.HttpResponseCache;
import fr.vriege.anilib.kernel.CapabilityKey;

/** Stable network capabilities published by the explicitly selected Network Bundle. */
public final class NetworkCapabilities {
    public static final CapabilityKey<AnilibHttpClient> HTTP_CLIENT =
            CapabilityKey.of("feature.network.http-client", AnilibHttpClient.class);
    public static final CapabilityKey<HttpCookieJar> COOKIES =
            CapabilityKey.of("feature.network.cookies", HttpCookieJar.class);
    public static final CapabilityKey<HttpRateLimiter> RATE_LIMITER =
            CapabilityKey.of("feature.network.rate-limiter", HttpRateLimiter.class);
    public static final CapabilityKey<HttpResponseCache> RESPONSE_CACHE =
            CapabilityKey.of("feature.network.response-cache", HttpResponseCache.class);

    private NetworkCapabilities() {
    }
}
