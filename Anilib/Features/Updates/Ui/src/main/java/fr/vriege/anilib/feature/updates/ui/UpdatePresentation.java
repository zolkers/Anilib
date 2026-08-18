package fr.vriege.anilib.feature.updates.ui;

import fr.vriege.anilib.feature.updates.LibraryUpdatePolicy;
import fr.vriege.anilib.feature.updates.LibraryUpdateSnapshot;

import java.util.concurrent.CompletableFuture;

/** Platform-neutral entry point for the shared Updates tab and settings. */
public interface UpdatePresentation {
    LibraryUpdateSnapshot snapshot();

    CompletableFuture<LibraryUpdateSnapshot> refresh();

    boolean cancel();

    void configure(LibraryUpdatePolicy policy);

    void markAllRead();

    AutoCloseable observe(Runnable listener);
}
