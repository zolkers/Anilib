package fr.vriege.anilib.feature.reader.runtime;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Least-recently-used page cache shared by every open chapter.
 *
 * <p>The budget is deliberately global rather than per chapter. Continuous reading keeps several
 * chapters open at once, and a per-chapter budget multiplies by the number of open chapters, so
 * memory grows with how far the reader has scrolled instead of staying bounded.
 *
 * <p>Entries are keyed by chapter so a closing chapter can drop its own pages without disturbing
 * the rest of the window.
 */
final class ReaderPageCache {
    private final long maximumBytes;
    private final Map<Key, byte[]> entries = new LinkedHashMap<>(64, 0.75f, true);
    private long cachedBytes;

    ReaderPageCache(long maximumBytes) {
        if (maximumBytes <= 0) {
            throw new IllegalArgumentException("maximumBytes must be positive");
        }
        this.maximumBytes = maximumBytes;
    }

    record Key(Object owner, int index) {
        Key {
            Objects.requireNonNull(owner, "owner must not be null");
        }
    }

    /**
     * Returns the cached page, or null. The array is shared with the cache and callers must treat
     * it as immutable: copying every hit would defeat the point of caching decoded bytes.
     */
    synchronized byte[] get(Key key) {
        return entries.get(key);
    }

    synchronized void put(Key key, byte[] bytes) {
        if (bytes.length > maximumBytes) {
            return;
        }
        byte[] previous = entries.put(key, bytes);
        if (previous != null) {
            cachedBytes -= previous.length;
        }
        cachedBytes += bytes.length;
        evictDown();
    }

    /** Drops every page belonging to one chapter, called when that chapter leaves the window. */
    synchronized void evictOwner(Object owner) {
        entries.entrySet().removeIf(entry -> {
            if (!entry.getKey().owner().equals(owner)) {
                return false;
            }
            cachedBytes -= entry.getValue().length;
            return true;
        });
    }

    synchronized long cachedBytes() {
        return cachedBytes;
    }

    private void evictDown() {
        var iterator = entries.entrySet().iterator();
        while (cachedBytes > maximumBytes && iterator.hasNext()) {
            Map.Entry<Key, byte[]> oldest = iterator.next();
            cachedBytes -= oldest.getValue().length;
            iterator.remove();
        }
    }
}
