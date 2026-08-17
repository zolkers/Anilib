package fr.vriege.anilib.feature.source;

import java.util.List;

/** Optional source capability for Aniyomi-style browse, search, filters, and preferences. */
public interface CatalogueSource extends Source {
    SourcePage popular(SourceBrowseRequest request);

    default boolean supportsLatest() {
        return false;
    }

    default SourcePage latest(SourceBrowseRequest request) {
        return popular(request);
    }

    SourcePage search(SourceSearchRequest request);

    default List<SourceFilterDefinition> filters() {
        return List.of();
    }

    default List<SourcePreferenceDefinition> preferences() {
        return List.of();
    }
}
