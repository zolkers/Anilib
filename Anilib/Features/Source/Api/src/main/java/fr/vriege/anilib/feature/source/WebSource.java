package fr.vriege.anilib.feature.source;

import java.net.URI;
import java.util.Optional;

/** Optional source contract for opening its website and catalogue titles in an embedded browser. */
public interface WebSource extends Source {
    URI homePage();

    default Optional<URI> titlePage(SourceCatalogueItemId itemId) {
        return Optional.empty();
    }
}
