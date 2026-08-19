package fr.vriege.anilib.framework.http;

import fr.vriege.anilib.foundation.validation.Preconditions;

import java.time.Duration;

/**
 * Per-request policy for reading and writing the HTTP response cache.
 *
 * <p>A bypass policy neither reads nor writes cached responses and therefore
 * has a zero time to live. Cache-enabled policies require a strictly positive
 * time to live. Only {@link HttpMethod#GET GET} requests may carry a
 * cache-enabled policy.</p>
 *
 * @param mode       the cache access strategy
 * @param timeToLive how long a successful response may remain fresh
 *
 * @see HttpRequest.Builder#cache(HttpCachePolicy)
 */
public record HttpCachePolicy(Mode mode, Duration timeToLive) {
    private static final HttpCachePolicy BYPASS = new HttpCachePolicy(Mode.BYPASS, Duration.ZERO);

    /**
     * Creates a cache policy and validates its mode/retention combination.
     *
     * @throws NullPointerException if {@code mode} or {@code timeToLive} is
     *                              {@code null}
     * @throws IllegalArgumentException if bypass has a non-zero time to live or
     *                                  a cache-enabled mode has a non-positive
     *                                  time to live
     */
    public HttpCachePolicy {
        Preconditions.requireNonNull(mode, "mode");
        Preconditions.requireNonNull(timeToLive, "timeToLive");
        if (mode == Mode.BYPASS && !timeToLive.isZero()) {
            throw new IllegalArgumentException("A bypass policy cannot retain a response");
        }
        if (mode != Mode.BYPASS && (timeToLive.isZero() || timeToLive.isNegative())) {
            throw new IllegalArgumentException("A caching policy requires a positive time to live");
        }
    }

    /**
     * Returns the shared policy that bypasses all response-cache access.
     *
     * @return a zero-retention bypass policy
     */
    public static HttpCachePolicy bypass() {
        return BYPASS;
    }

    /**
     * Creates a policy that serves a fresh cached response when available and
     * otherwise fetches and stores a network response.
     *
     * @param timeToLive the strictly positive retention duration
     * @return a prefer-cache policy
     * @throws NullPointerException if {@code timeToLive} is {@code null}
     * @throws IllegalArgumentException if {@code timeToLive} is zero or
     *                                  negative
     */
    public static HttpCachePolicy preferCache(Duration timeToLive) {
        return new HttpCachePolicy(Mode.PREFER_CACHE, timeToLive);
    }

    /**
     * Creates a policy that skips cache reads, fetches a network response, and
     * replaces the cached value when the response is eligible.
     *
     * @param timeToLive the strictly positive retention duration
     * @return a refresh policy
     * @throws NullPointerException if {@code timeToLive} is {@code null}
     * @throws IllegalArgumentException if {@code timeToLive} is zero or
     *                                  negative
     */
    public static HttpCachePolicy refresh(Duration timeToLive) {
        return new HttpCachePolicy(Mode.REFRESH, timeToLive);
    }

    /**
     * Reports whether this policy permits a cache lookup before network access.
     *
     * @return {@code true} only for {@link Mode#PREFER_CACHE}
     */
    public boolean reads() {
        return mode == Mode.PREFER_CACHE;
    }

    /**
     * Reports whether this policy permits an eligible network response to be
     * cached.
     *
     * @return {@code false} only for {@link Mode#BYPASS}
     */
    public boolean writes() {
        return mode != Mode.BYPASS;
    }

    /** Describes how a request interacts with the response cache. */
    public enum Mode {
        /** Does not read or write the response cache. */
        BYPASS,

        /** Reads a fresh entry first and writes a successful cache miss. */
        PREFER_CACHE,

        /** Skips reads and replaces the entry with a successful response. */
        REFRESH
    }
}
