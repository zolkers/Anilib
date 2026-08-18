package fr.vriege.anilib.feature.library.runtime;

import fr.vriege.anilib.feature.library.LibraryCatalog;
import fr.vriege.anilib.feature.library.LibraryItem;
import fr.vriege.anilib.feature.library.LibraryItemId;
import fr.vriege.anilib.feature.library.LibraryStorageException;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

public final class FileLibraryCatalog implements LibraryCatalog {
    private static final Comparator<LibraryItem> DISPLAY_ORDER =
            Comparator.comparing(LibraryItem::title, String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(LibraryItem::id);

    private final LibraryFileStore store;
    private final Map<LibraryItemId, LibraryItem> items = new LinkedHashMap<>();
    private final CopyOnWriteArrayList<Runnable> listeners = new CopyOnWriteArrayList<>();

    public FileLibraryCatalog(Path storageFile) {
        store = new LibraryFileStore(Objects.requireNonNull(storageFile, "storageFile must not be null"));
        try {
            LibraryFileStore.LoadResult loaded = store.load();
            loaded.items().forEach(item -> items.put(item.id(), item));
            if (loaded.migrationRequired()) {
                store.save(items.values());
            }
        } catch (IOException exception) {
            throw failure("load or migrate", exception);
        }
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
        Map<LibraryItemId, LibraryItem> next = new LinkedHashMap<>(items);
        next.put(item.id(), item);
        persistAndReplace(next.values());
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
        persistAndReplace(next.values());
        notifyListeners();
    }

    @Override
    public synchronized boolean remove(LibraryItemId id) {
        Objects.requireNonNull(id, "id must not be null");
        if (!items.containsKey(id)) {
            return false;
        }
        Map<LibraryItemId, LibraryItem> next = new LinkedHashMap<>(items);
        next.remove(id);
        persistAndReplace(next.values());
        notifyListeners();
        return true;
    }

    @Override
    public AutoCloseable observe(Runnable listener) {
        Runnable value = Objects.requireNonNull(listener, "listener must not be null");
        listeners.add(value);
        return () -> listeners.remove(value);
    }

    private void persistAndReplace(Collection<LibraryItem> next) {
        try {
            store.save(next);
            items.clear();
            next.forEach(item -> items.put(item.id(), item));
        } catch (IOException exception) {
            throw failure("write", exception);
        }
    }

    private static LibraryStorageException failure(String operation, IOException cause) {
        return new LibraryStorageException("Unable to " + operation + " the Anilib library", cause);
    }

    private void notifyListeners() {
        listeners.forEach(Runnable::run);
    }
}
