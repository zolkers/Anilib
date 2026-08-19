package fr.vriege.anilib.feature.reader;

import fr.vriege.anilib.feature.library.LibraryItemId;
import fr.vriege.anilib.feature.source.SourceContentUnit;
import fr.vriege.anilib.feature.source.SourceContentUnitId;
import fr.vriege.anilib.feature.source.SourceCatalogueItemId;

import java.util.List;

public interface ReaderService {
    boolean canOpen(LibraryItemId libraryItemId);

    List<SourceContentUnit> contentUnits(LibraryItemId libraryItemId);

    List<SourceContentUnit> contentUnits(SourceCatalogueItemId itemId);

    ReaderSession open(LibraryItemId libraryItemId);

    ReaderSession open(LibraryItemId libraryItemId, SourceContentUnitId contentUnitId);

    ReaderSession open(String title, SourceContentUnitId contentUnitId);
}
