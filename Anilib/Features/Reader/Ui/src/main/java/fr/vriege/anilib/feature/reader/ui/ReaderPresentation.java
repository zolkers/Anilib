package fr.vriege.anilib.feature.reader.ui;

import fr.vriege.anilib.feature.library.LibraryItemId;
import fr.vriege.anilib.feature.source.SourceContentUnit;
import fr.vriege.anilib.feature.source.SourceCatalogueItemId;
import fr.vriege.anilib.feature.source.SourceContentUnitId;

import java.util.List;
import java.util.Set;

public interface ReaderPresentation {
    boolean canOpen(LibraryItemId libraryItemId);

    Set<String> readContentIds(LibraryItemId libraryItemId);

    List<SourceContentUnit> contentUnits(LibraryItemId libraryItemId);

    List<SourceContentUnit> contentUnits(SourceCatalogueItemId itemId);

    ReaderController open(LibraryItemId libraryItemId);

    ReaderController open(LibraryItemId libraryItemId, SourceContentUnitId contentUnitId);

    ReaderController open(String title, SourceContentUnitId contentUnitId);
}
