package fr.vriege.anilib.feature.reader;

import fr.vriege.anilib.feature.library.LibraryItemId;

/** Opens paged library titles through their registered source origin. */
public interface ReaderService {
    boolean canOpen(LibraryItemId libraryItemId);

    ReaderSession open(LibraryItemId libraryItemId);
}
