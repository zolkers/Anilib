package fr.vriege.anilib.feature.library.core;

import fr.vriege.anilib.feature.library.LibraryCatalog;
import fr.vriege.anilib.feature.library.LibraryItem;
import fr.vriege.anilib.feature.library.LibraryItemId;

import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

public final class InMemoryLibraryCatalog implements LibraryCatalog {
    private static final Comparator<LibraryItem> DISPLAY_ORDER =
            Comparator.comparing(LibraryItem::title, String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(LibraryItem::id);

    private final Map<LibraryItemId, LibraryItem> items = new LinkedHashMap<>();
    private final CopyOnWriteArrayList<Runnable> listeners = new CopyOnWriteArrayList<>();

    public InMemoryLibraryCatalog() {
    }

    @Override
    public synchronized List<LibraryItem> snapshot() {
        return items.values().stream().sorted(DISPLAY_ORDER).toList();
    }

    @Override
    public synchronized Optional<LibraryItem> find(LibraryItemId id) {
        return Optional.ofNullable(items.get(Objects.requireNonNull(id, "id must not be null")));
    }

    @Override
    public synchronized void save(LibraryItem item) {
        Objects.requireNonNull(item, "item must not be null");
        items.put(item.id(), item);
        notifyListeners();
    }

    @Override
    public synchronized void replaceAll(Collection<LibraryItem> replacement) {
        Objects.requireNonNull(replacement, "replacement must not be null");
        Map<LibraryItemId, LibraryItem> next = new LinkedHashMap<>();
        for (LibraryItem item : replacement) {
            Objects.requireNonNull(item, "replacement must not contain null items");
            if (next.putIfAbsent(item.id(), item) != null) {
                throw new IllegalArgumentException("replacement contains duplicate library item ids");
            }
        }
        items.clear();
        items.putAll(next);
        notifyListeners();
    }

    @Override
    public synchronized boolean remove(LibraryItemId id) {
        boolean removed = items.remove(Objects.requireNonNull(id, "id must not be null")) != null;
        if (removed) {
            notifyListeners();
        }
        return removed;
    }

    @Override
    public AutoCloseable observe(Runnable listener) {
        Runnable value = Objects.requireNonNull(listener, "listener must not be null");
        listeners.add(value);
        return () -> listeners.remove(value);
    }

    private void notifyListeners() {
        listeners.forEach(Runnable::run);
    }
}
