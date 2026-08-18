package fr.vriege.anilib.feature.updates;

import java.util.concurrent.CompletableFuture;

public interface LibraryUpdateService {
    LibraryUpdateSnapshot snapshot();

    CompletableFuture<LibraryUpdateSnapshot> runNow();

    boolean cancel();

    void configure(LibraryUpdatePolicy policy);

    void markAllRead();

    AutoCloseable observe(Runnable listener);
}
