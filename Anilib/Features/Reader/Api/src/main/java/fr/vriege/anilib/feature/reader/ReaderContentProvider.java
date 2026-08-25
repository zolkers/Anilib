package fr.vriege.anilib.feature.reader;

import fr.vriege.anilib.feature.source.SourceCatalogueItemId;
import fr.vriege.anilib.feature.source.SourcePageResource;

import java.util.Optional;

public interface ReaderContentProvider {
    /**
     * Finds downloaded reader content for a title.
     *
     * <p>When {@code preferredContentId} is present, implementations must return only that exact
     * content unit. Returning another downloaded unit would incorrectly prevent the reader from
     * falling back to the online source for the requested chapter.</p>
     */
    Optional<ReaderContent> find(SourceCatalogueItemId itemId, Optional<String> preferredContentId);

    byte[] readPage(SourcePageResource page);

    boolean sourceFallbackAllowed();
}
