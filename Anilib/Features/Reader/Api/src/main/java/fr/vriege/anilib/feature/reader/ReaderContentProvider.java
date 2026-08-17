package fr.vriege.anilib.feature.reader;

import fr.vriege.anilib.feature.source.SourceCatalogueItemId;
import fr.vriege.anilib.feature.source.SourcePageResource;

import java.util.Optional;

/** Optional Bundle-owned page provider used for downloaded and offline content. */
public interface ReaderContentProvider {
    Optional<ReaderContent> find(SourceCatalogueItemId itemId, Optional<String> preferredContentId);

    byte[] readPage(SourcePageResource page);

    /** Whether Reader may contact the original Source when no alternate content exists. */
    boolean sourceFallbackAllowed();
}
