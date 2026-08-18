package fr.vriege.anilib.feature.reader.ui;

import fr.vriege.anilib.feature.library.LibraryItemId;
import fr.vriege.anilib.feature.source.SourceContentUnit;

import java.util.List;

public interface ReaderPresentation {
    boolean canOpen(LibraryItemId libraryItemId);

    List<SourceContentUnit> contentUnits(LibraryItemId libraryItemId);

    ReaderController open(LibraryItemId libraryItemId);
}
