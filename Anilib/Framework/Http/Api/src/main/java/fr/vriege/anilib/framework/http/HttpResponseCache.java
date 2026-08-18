package fr.vriege.anilib.framework.http;

import java.util.Optional;

public interface HttpResponseCache {
    Optional<HttpCacheEntry> find(String key);

    void store(String key, HttpCacheEntry entry);

    void invalidate(String key);

    void clear();
}
