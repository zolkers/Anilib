package fr.vriege.anilib.feature.reader;

import fr.vriege.anilib.feature.library.LibraryItemId;

import java.util.Collection;
import java.util.Set;
import java.util.function.Consumer;

public interface ReaderReadStateStore {
    Set<String> readContentIds(LibraryItemId libraryItemId);

    void setRead(LibraryItemId libraryItemId, String contentId, boolean read);

    default void setRead(LibraryItemId libraryItemId, Collection<String> contentIds, boolean read) {
        for (String contentId : Set.copyOf(contentIds)) {
            setRead(libraryItemId, contentId, read);
        }
    }

    AutoCloseable observe(Consumer<ReaderReadEvent> listener);
}
