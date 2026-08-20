package fr.vriege.anilib.platform.desktopextensionhost.compat.android.content;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public final class SourcePreferences implements SharedPreferences, SharedPreferences.Editor {
    private static final Map<Long, SourcePreferences> SOURCES = new ConcurrentHashMap<>();
    private final Map<String, Object> values = new HashMap<>();

    private SourcePreferences() {
    }

    public static SharedPreferences forSource(long sourceId) {
        return SOURCES.computeIfAbsent(sourceId, ignored -> new SourcePreferences());
    }

    @Override public synchronized Map<String, ?> getAll() { return Map.copyOf(values); }
    @Override public synchronized String getString(String key, String fallback) {
        return value(key, String.class, fallback);
    }
    @Override public synchronized Set<String> getStringSet(String key, Set<String> fallback) {
        return stringSet(key, fallback);
    }
    @Override public synchronized int getInt(String key, int fallback) {
        return number(key, fallback).intValue();
    }
    @Override public synchronized long getLong(String key, long fallback) {
        return number(key, fallback).longValue();
    }
    @Override public synchronized float getFloat(String key, float fallback) {
        return number(key, fallback).floatValue();
    }
    @Override public synchronized boolean getBoolean(String key, boolean fallback) {
        return value(key, Boolean.class, fallback);
    }
    @Override public synchronized boolean contains(String key) { return values.containsKey(key); }
    @Override public Editor edit() { return this; }
    @Override public synchronized Editor putString(String key, String value) { return put(key, value); }
    @Override public synchronized Editor putStringSet(String key, Set<String> value) {
        return put(key, Set.copyOf(value));
    }
    @Override public synchronized Editor putInt(String key, int value) { return put(key, value); }
    @Override public synchronized Editor putLong(String key, long value) { return put(key, value); }
    @Override public synchronized Editor putFloat(String key, float value) { return put(key, value); }
    @Override public synchronized Editor putBoolean(String key, boolean value) { return put(key, value); }
    @Override public synchronized Editor remove(String key) { values.remove(key); return this; }
    @Override public synchronized Editor clear() { values.clear(); return this; }
    @Override public boolean commit() { return true; }
    @Override public void apply() { }

    private Editor put(String key, Object value) { values.put(key, value); return this; }
    private Number number(String key, Number fallback) { return value(key, Number.class, fallback); }
    private <T> T value(String key, Class<T> type, T fallback) {
        Object value = values.get(key);
        return type.isInstance(value) ? type.cast(value) : fallback;
    }

    private Set<String> stringSet(String key, Set<String> fallback) {
        Object value = values.get(key);
        if (!(value instanceof Set<?> valuesSet)
                || valuesSet.stream().anyMatch(element -> !(element instanceof String))) {
            return fallback;
        }
        return valuesSet.stream().map(String.class::cast).collect(Collectors.toUnmodifiableSet());
    }
}
