package fr.vriege.anilib.feature.updates.ui;

import fr.vriege.anilib.feature.updates.LibraryUpdatePolicy;
import fr.vriege.anilib.feature.updates.LibraryUpdateEventId;
import fr.vriege.anilib.feature.updates.LibraryUpdateSnapshot;

import java.util.concurrent.CompletableFuture;
import java.util.Set;

public interface UpdatePresentation {
    LibraryUpdateSnapshot snapshot();

    CompletableFuture<LibraryUpdateSnapshot> refresh();

    boolean cancel();

    void configure(LibraryUpdatePolicy policy);

    void markAllRead();

    void setEventsRead(Set<LibraryUpdateEventId> ids, boolean read);

    void removeEvents(Set<LibraryUpdateEventId> ids);

    AutoCloseable observe(Runnable listener);
}
