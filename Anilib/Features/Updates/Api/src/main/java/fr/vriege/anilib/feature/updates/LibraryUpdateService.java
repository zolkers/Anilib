package fr.vriege.anilib.feature.updates;

import java.util.concurrent.CompletableFuture;

/** Background-safe library refresh orchestration shared by all platforms. */
public interface LibraryUpdateService {
    LibraryUpdateSnapshot snapshot();

    CompletableFuture<LibraryUpdateSnapshot> runNow();

    boolean cancel();

    void configure(LibraryUpdatePolicy policy);

    void markAllRead();

    AutoCloseable observe(Runnable listener);
}
