package fr.vriege.anilib.platform.desktopextensionhost.compat.android.util;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/* Android-compatible, size-accounted access-order cache. */
public class LruCache<K, V> {
    private final LinkedHashMap<K, V> entries = new LinkedHashMap<>(0, 0.75f, true);
    private int maximum;
    private int size;
    private int hits;
    private int misses;
    private int puts;
    private int creates;
    private int evictions;

    public LruCache(int maxSize) {
        if (maxSize <= 0) throw new IllegalArgumentException("maxSize must be positive");
        maximum = maxSize;
    }

    public final V get(K key) {
        Objects.requireNonNull(key, "key");
        synchronized (this) {
            V value = entries.get(key);
            if (value != null) {
                hits++;
                return value;
            }
            misses++;
        }
        V created = create(key);
        if (created == null) return null;
        V previous;
        synchronized (this) {
            creates++;
            previous = entries.put(key, created);
            if (previous != null) entries.put(key, previous);
            else size += checkedSize(key, created);
        }
        if (previous != null) {
            entryRemoved(false, key, created, previous);
            return previous;
        }
        trimToSize(maxSize());
        return created;
    }

    public final V put(K key, V value) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(value, "value");
        V previous;
        synchronized (this) {
            int added = checkedSize(key, value);
            previous = entries.put(key, value);
            size += added - (previous == null ? 0 : checkedSize(key, previous));
            puts++;
        }
        if (previous != null) entryRemoved(false, key, previous, value);
        trimToSize(maxSize());
        return previous;
    }

    public final V remove(K key) {
        V removed;
        synchronized (this) {
            removed = entries.remove(Objects.requireNonNull(key, "key"));
            if (removed != null) size -= checkedSize(key, removed);
        }
        if (removed != null) entryRemoved(false, key, removed, null);
        return removed;
    }

    public void trimToSize(int limit) {
        while (true) {
            K key;
            V value;
            synchronized (this) {
                if (size < 0 || entries.isEmpty() && size != 0)
                    throw new IllegalStateException("Inconsistent sizeOf");
                if (size <= limit || entries.isEmpty()) return;
                Map.Entry<K, V> eldest = entries.entrySet().iterator().next();
                key = eldest.getKey();
                value = eldest.getValue();
                entries.remove(key);
                size -= checkedSize(key, value);
                evictions++;
            }
            entryRemoved(true, key, value, null);
        }
    }

    public final void resize(int maxSize) {
        if (maxSize <= 0) throw new IllegalArgumentException("maxSize must be positive");
        synchronized (this) {
            maximum = maxSize;
        }
        trimToSize(maxSize);
    }

    public final void evictAll() {
        trimToSize(-1);
    }

    public final synchronized int size() {
        return size;
    }

    public final synchronized int maxSize() {
        return maximum;
    }

    public final synchronized int hitCount() {
        return hits;
    }

    public final synchronized int missCount() {
        return misses;
    }

    public final synchronized int putCount() {
        return puts;
    }

    public final synchronized int createCount() {
        return creates;
    }

    public final synchronized int evictionCount() {
        return evictions;
    }

    public final synchronized Map<K, V> snapshot() {
        return new LinkedHashMap<>(entries);
    }

    protected V create(K key) {
        return null;
    }

    protected int sizeOf(K key, V value) {
        return 1;
    }

    protected void entryRemoved(boolean evicted, K key, V oldValue, V newValue) {}

    private int checkedSize(K key, V value) {
        int result = sizeOf(key, value);
        if (result < 0) throw new IllegalStateException("Negative sizeOf");
        return result;
    }
}
