package fr.vriege.anilib.framework.http;

import fr.vriege.anilib.foundation.validation.Preconditions;

import java.time.Instant;

/** One cache-owned network response and its absolute expiry. */
public record HttpCacheEntry(HttpResponse response, Instant expiresAt) {
    public HttpCacheEntry {
        response = Preconditions.requireNonNull(response, "response").asNetworkResponse();
        Preconditions.requireNonNull(expiresAt, "expiresAt");
    }

    public boolean isFresh(Instant instant) {
        return Preconditions.requireNonNull(instant, "instant").isBefore(expiresAt);
    }
}
