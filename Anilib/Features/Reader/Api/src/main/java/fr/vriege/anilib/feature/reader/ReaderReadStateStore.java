package fr.vriege.anilib.feature.reader;

import fr.vriege.anilib.feature.library.LibraryItemId;

import java.util.Set;

public interface ReaderReadStateStore {
    Set<String> readContentIds(LibraryItemId libraryItemId);

    void setRead(LibraryItemId libraryItemId, String contentId, boolean read);
}
