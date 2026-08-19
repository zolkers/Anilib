package fr.vriege.anilib.framework.http;

import fr.vriege.anilib.foundation.validation.Preconditions;

import java.time.Instant;

/**
 * A network response retained until an exclusive expiration instant.
 *
 * <p>The stored response is normalized with
 * {@link HttpResponse#asNetworkResponse()}, ensuring that cache provenance is
 * attached only when the entry is served. An entry is fresh strictly before
 * {@link #expiresAt()} and stale at or after that instant.</p>
 *
 * @param response  the response snapshot to retain
 * @param expiresAt the exclusive expiration instant
 */
public record HttpCacheEntry(HttpResponse response, Instant expiresAt) {
    /**
     * Creates a normalized cache entry.
     *
     * @throws NullPointerException if {@code response} or {@code expiresAt} is
     *                              {@code null}
     */
    public HttpCacheEntry {
        response = Preconditions.requireNonNull(response, "response").asNetworkResponse();
        Preconditions.requireNonNull(expiresAt, "expiresAt");
    }

    /**
     * Tests whether this entry is fresh at a given instant.
     *
     * @param instant the instant to test
     * @return {@code true} if {@code instant} is before the expiration instant
     * @throws NullPointerException if {@code instant} is {@code null}
     */
    public boolean isFresh(Instant instant) {
        return Preconditions.requireNonNull(instant, "instant").isBefore(expiresAt);
    }
}
