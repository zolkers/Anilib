package fr.vriege.anilib.feature.reader;

import fr.vriege.anilib.feature.source.SourceCatalogueItemId;
import fr.vriege.anilib.feature.source.SourcePageResource;

import java.util.Optional;

public interface ReaderContentProvider {
    Optional<ReaderContent> find(SourceCatalogueItemId itemId, Optional<String> preferredContentId);

    byte[] readPage(SourcePageResource page);

    boolean sourceFallbackAllowed();
}
