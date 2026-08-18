package fr.vriege.anilib.feature.settings.runtime;

import fr.vriege.anilib.feature.settings.UnusedDataCleaner;
import fr.vriege.anilib.feature.settings.UnusedDataCleanupResult;
import fr.vriege.anilib.feature.settings.UnusedDataMaintenance;
import fr.vriege.anilib.feature.settings.UnusedDataRegistrar;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class DefaultUnusedDataMaintenance implements UnusedDataMaintenance, UnusedDataRegistrar {
    private final Map<String, UnusedDataCleaner> cleaners = new LinkedHashMap<>();

    public DefaultUnusedDataMaintenance() {
    }

    @Override
    public synchronized AutoCloseable register(String owner, UnusedDataCleaner cleaner) {
        String name = Objects.requireNonNull(owner, "owner must not be null").strip();
        if (name.isEmpty()) {
            throw new IllegalArgumentException("owner must not be blank");
        }
        UnusedDataCleaner value = Objects.requireNonNull(cleaner, "cleaner must not be null");
        if (cleaners.putIfAbsent(name, value) != null) {
            throw new IllegalStateException("Duplicate unused-data cleaner: " + name);
        }
        return () -> unregister(name, value);
    }

    @Override
    public synchronized UnusedDataCleanupResult clean() {
        Map<String, Integer> removed = new LinkedHashMap<>();
        cleaners.forEach((owner, cleaner) -> removed.put(owner, cleaner.clean()));
        return new UnusedDataCleanupResult(removed);
    }

    private synchronized void unregister(String owner, UnusedDataCleaner cleaner) {
        cleaners.remove(owner, cleaner);
    }
}
