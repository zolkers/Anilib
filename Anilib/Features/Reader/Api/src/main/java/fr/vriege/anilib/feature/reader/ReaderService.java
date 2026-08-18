package fr.vriege.anilib.feature.reader;

import fr.vriege.anilib.feature.library.LibraryItemId;
import fr.vriege.anilib.feature.source.SourceContentUnit;
import fr.vriege.anilib.feature.source.SourceContentUnitId;

import java.util.List;

public interface ReaderService {
    boolean canOpen(LibraryItemId libraryItemId);

    List<SourceContentUnit> contentUnits(LibraryItemId libraryItemId);

    ReaderSession open(LibraryItemId libraryItemId);

    ReaderSession open(LibraryItemId libraryItemId, SourceContentUnitId contentUnitId);
}
