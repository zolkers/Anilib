package fr.vriege.anilib.feature.reader.ui;

import fr.vriege.anilib.feature.library.LibraryItemId;
import fr.vriege.anilib.feature.reader.ReaderService;

import java.util.Objects;

public final class DefaultReaderPresentation implements ReaderPresentation {
    private final ReaderService reader;

    public DefaultReaderPresentation(ReaderService reader) {
        this.reader = Objects.requireNonNull(reader, "reader must not be null");
    }

    @Override
    public boolean canOpen(LibraryItemId libraryItemId) {
        return reader.canOpen(libraryItemId);
    }

    @Override
    public ReaderController open(LibraryItemId libraryItemId) {
        return new ReaderController(reader.open(libraryItemId));
    }
}
