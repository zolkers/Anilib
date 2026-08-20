package fr.vriege.anilib.feature.reader.ui;

import fr.vriege.anilib.feature.library.LibraryItemId;
import fr.vriege.anilib.feature.source.SourceContentUnit;
import fr.vriege.anilib.feature.source.SourceCatalogueItemId;
import fr.vriege.anilib.feature.source.SourceContentUnitId;

import java.util.List;

public interface ReaderPresentation {
    boolean canOpen(LibraryItemId libraryItemId);

    List<SourceContentUnit> contentUnits(LibraryItemId libraryItemId);

    List<SourceContentUnit> contentUnits(SourceCatalogueItemId itemId);

    ReaderController open(LibraryItemId libraryItemId);

    ReaderController open(LibraryItemId libraryItemId, SourceContentUnitId contentUnitId);

    ReaderController open(String title, SourceContentUnitId contentUnitId);
}
