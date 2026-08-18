package fr.vriege.anilib.feature.reader.ui;

import fr.vriege.anilib.feature.library.LibraryItemId;

public interface ReaderPresentation {
    boolean canOpen(LibraryItemId libraryItemId);

    ReaderController open(LibraryItemId libraryItemId);
}
