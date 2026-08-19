package fr.vriege.anilib.framework.http;

import java.util.Optional;

/**
 * Storage port for HTTP responses addressed by opaque policy-generated keys.
 *
 * <p>The cache does not derive keys or decide eligibility. The shared
 * {@link AnilibHttpClient} supplies keys that incorporate the request method,
 * URI, and effective headers, and applies {@link HttpCachePolicy}. Cache
 * implementations may discard stale or invalid entries during lookup.</p>
 */
public interface HttpResponseCache {
    /**
     * Finds a cache entry by opaque key.
     *
     * @param key the non-null, non-blank opaque cache key
     * @return the entry, or an empty optional when absent or discarded
     * @throws NullPointerException if {@code key} is {@code null}
     * @throws IllegalArgumentException if {@code key} is blank
     * @throws HttpException if the cache cannot complete the lookup
     */
    Optional<HttpCacheEntry> find(String key);

    /**
     * Stores or replaces an entry under an opaque key.
     *
     * @param key   the non-null, non-blank opaque cache key
     * @param entry the non-null entry to retain
     * @throws NullPointerException if either argument is {@code null}
     * @throws IllegalArgumentException if {@code key} is blank
     * @throws HttpException if the entry cannot be stored
     */
    void store(String key, HttpCacheEntry entry);

    /**
     * Removes the entry associated with an opaque key, if present.
     *
     * @param key the non-null, non-blank opaque cache key
     * @throws NullPointerException if {@code key} is {@code null}
     * @throws IllegalArgumentException if {@code key} is blank
     * @throws HttpException if the entry cannot be invalidated
     */
    void invalidate(String key);

    /**
     * Removes all entries owned by this response cache.
     *
     * @throws HttpException if the cache cannot be cleared
     */
    void clear();
}
