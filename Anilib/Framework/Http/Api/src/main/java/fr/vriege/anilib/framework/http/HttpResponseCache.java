package fr.vriege.anilib.framework.http;

import java.util.Optional;

/** Response cache boundary; keys are opaque and owned by the transport. */
public interface HttpResponseCache {
    Optional<HttpCacheEntry> find(String key);

    void store(String key, HttpCacheEntry entry);

    void invalidate(String key);

    void clear();
}
