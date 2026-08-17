package fr.vriege.anilib.feature.reader.ui;

import fr.vriege.anilib.feature.library.LibraryItemId;

/** Platform-neutral entry point consumed by Android and desktop reader screens. */
public interface ReaderPresentation {
    boolean canOpen(LibraryItemId libraryItemId);

    ReaderController open(LibraryItemId libraryItemId);
}
