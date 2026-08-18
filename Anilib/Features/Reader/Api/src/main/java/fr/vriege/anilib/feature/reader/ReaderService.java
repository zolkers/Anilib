package fr.vriege.anilib.feature.reader;

import fr.vriege.anilib.feature.library.LibraryItemId;

public interface ReaderService {
    boolean canOpen(LibraryItemId libraryItemId);

    ReaderSession open(LibraryItemId libraryItemId);
}
