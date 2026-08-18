package fr.vriege.anilib.feature.updates.ui;

import fr.vriege.anilib.feature.updates.LibraryUpdatePolicy;
import fr.vriege.anilib.feature.updates.LibraryUpdateService;
import fr.vriege.anilib.feature.updates.LibraryUpdateSnapshot;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

public final class DefaultUpdatePresentation implements UpdatePresentation {
    private final LibraryUpdateService service;

    public DefaultUpdatePresentation(LibraryUpdateService service) {
        this.service = Objects.requireNonNull(service, "service must not be null");
    }

    @Override
    public LibraryUpdateSnapshot snapshot() {
        return service.snapshot();
    }

    @Override
    public CompletableFuture<LibraryUpdateSnapshot> refresh() {
        return service.runNow();
    }

    @Override
    public boolean cancel() {
        return service.cancel();
    }

    @Override
    public void configure(LibraryUpdatePolicy policy) {
        service.configure(policy);
    }

    @Override
    public void markAllRead() {
        service.markAllRead();
    }

    @Override
    public AutoCloseable observe(Runnable listener) {
        return service.observe(listener);
    }
}
