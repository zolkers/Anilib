package fr.vriege.anilib.feature.updates;

import java.util.Set;
import java.util.concurrent.CompletableFuture;

public interface LibraryUpdateService {
    LibraryUpdateSnapshot snapshot();

    CompletableFuture<LibraryUpdateSnapshot> runNow();

    boolean cancel();

    void configure(LibraryUpdatePolicy policy);

    void markAllRead();

    void setEventsRead(Set<LibraryUpdateEventId> ids, boolean read);

    void removeEvents(Set<LibraryUpdateEventId> ids);

    AutoCloseable observe(Runnable listener);
}
