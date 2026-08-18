package fr.vriege.anilib.feature.reader.ui;

import fr.vriege.anilib.feature.library.LibraryItemId;
import fr.vriege.anilib.feature.reader.ReaderDisplayPreferenceStore;
import fr.vriege.anilib.feature.reader.ReaderService;
import fr.vriege.anilib.feature.reader.ReaderInteractionPreferenceStore;
import fr.vriege.anilib.feature.reader.ReaderReadStateStore;
import fr.vriege.anilib.feature.source.SourceContentUnit;

import java.util.List;
import java.util.Objects;

public final class DefaultReaderPresentation implements ReaderPresentation {
    private final ReaderService reader;
    private final ReaderInteractionPreferenceStore interactions;
    private final ReaderDisplayPreferenceStore display;
    private final ReaderReadStateStore readState;

    public DefaultReaderPresentation(
            ReaderService reader,
            ReaderInteractionPreferenceStore interactions,
            ReaderDisplayPreferenceStore display,
            ReaderReadStateStore readState) {
        this.reader = Objects.requireNonNull(reader, "reader must not be null");
        this.interactions = Objects.requireNonNull(interactions, "interactions must not be null");
        this.display = Objects.requireNonNull(display, "display must not be null");
        this.readState = Objects.requireNonNull(readState, "readState must not be null");
    }

    @Override
    public boolean canOpen(LibraryItemId libraryItemId) {
        return reader.canOpen(libraryItemId);
    }

    @Override
    public List<SourceContentUnit> contentUnits(LibraryItemId libraryItemId) {
        return reader.contentUnits(libraryItemId);
    }

    @Override
    public ReaderController open(LibraryItemId libraryItemId) {
        return new ReaderController(reader, libraryItemId, interactions, display, readState);
    }
}
