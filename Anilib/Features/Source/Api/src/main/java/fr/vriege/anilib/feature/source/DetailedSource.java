package fr.vriege.anilib.feature.source;

public interface DetailedSource extends Source {
    SourceTitleDetails details(SourceCatalogueItemId itemId);

    default SourceTitleDetails details(SourceCatalogueItem item) {
        return details(item.id());
    }
}
