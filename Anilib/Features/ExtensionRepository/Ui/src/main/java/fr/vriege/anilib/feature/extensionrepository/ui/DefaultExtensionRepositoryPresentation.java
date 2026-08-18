package fr.vriege.anilib.feature.extensionrepository.ui;

import fr.vriege.anilib.feature.extensionrepository.ExtensionRepositoryService;
import fr.vriege.anilib.feature.extensionrepository.ExtensionRepositorySnapshot;
import fr.vriege.anilib.foundation.validation.Preconditions;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Shared non-blocking presentation over the synchronous repository service. */
public final class DefaultExtensionRepositoryPresentation
        implements ExtensionRepositoryPresentation, AutoCloseable {
    private final ExtensionRepositoryService service;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "anilib-extension-repositories");
        thread.setDaemon(true);
        return thread;
    });
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();

    public DefaultExtensionRepositoryPresentation(ExtensionRepositoryService service) {
        this.service = Preconditions.requireNonNull(service, "service");
    }

    @Override
    public ExtensionRepositoryView snapshot() {
        Map<URI, ExtensionRepositorySnapshot> snapshots = service.snapshots().stream()
                .collect(java.util.stream.Collectors.toMap(
                        ExtensionRepositorySnapshot::indexUri,
                        value -> value));
        List<ExtensionRepositoryRow> rows = new ArrayList<>();
        for (URI repository : service.repositories()) {
            ExtensionRepositorySnapshot snapshot = snapshots.get(repository);
            rows.add(snapshot == null
                    ? new ExtensionRepositoryRow(repository, java.util.Optional.empty(), 0, java.util.Optional.empty())
                    : new ExtensionRepositoryRow(
                            repository,
                            java.util.Optional.of(snapshot.fetchedAt()),
                            snapshot.packages().size(),
                            snapshot.failure()));
        }
        return new ExtensionRepositoryView(rows, service.packages());
    }

    @Override
    public void add(String indexUrl) {
        service.add(URI.create(Preconditions.requireNonBlank(indexUrl, "indexUrl")));
        notifyListeners();
    }

    @Override
    public boolean remove(String indexUrl) {
        boolean removed = service.remove(URI.create(Preconditions.requireNonBlank(indexUrl, "indexUrl")));
        if (removed) {
            notifyListeners();
        }
        return removed;
    }

    @Override
    public CompletableFuture<ExtensionRepositoryView> refreshAll() {
        return CompletableFuture.supplyAsync(() -> {
            service.refreshAll();
            ExtensionRepositoryView view = snapshot();
            notifyListeners();
            return view;
        }, executor);
    }

    @Override
    public AutoCloseable observe(Runnable listener) {
        Runnable value = Preconditions.requireNonNull(listener, "listener");
        listeners.add(value);
        return () -> listeners.remove(value);
    }

    @Override
    public void close() {
        listeners.clear();
        executor.shutdownNow();
    }

    private void notifyListeners() {
        for (Runnable listener : listeners) {
            listener.run();
        }
    }
}
