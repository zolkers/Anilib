package fr.vriege.anilib.feature.source;

import java.util.List;

public interface PagedSource extends Source {
    List<SourceContentUnit> contentUnits(SourceCatalogueItemId itemId);

    List<SourcePageResource> pages(SourceContentUnitId contentUnitId);

    byte[] readPage(SourcePageResource page);
}
