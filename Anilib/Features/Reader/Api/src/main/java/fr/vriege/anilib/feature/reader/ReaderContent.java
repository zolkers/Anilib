package fr.vriege.anilib.feature.reader;

import fr.vriege.anilib.feature.source.SourceContentUnit;
import fr.vriege.anilib.feature.source.SourcePageResource;

import java.util.List;
import java.util.Objects;

/** Complete immutable page set supplied by an alternate Reader content provider. */
public record ReaderContent(SourceContentUnit contentUnit, List<SourcePageResource> pages) {
    public ReaderContent {
        Objects.requireNonNull(contentUnit, "contentUnit must not be null");
        pages = List.copyOf(pages);
        if (pages.isEmpty()) {
            throw new IllegalArgumentException("pages must not be empty");
        }
        for (int index = 0; index < pages.size(); index++) {
            SourcePageResource page = pages.get(index);
            if (!page.contentUnitId().equals(contentUnit.id()) || page.index() != index) {
                throw new IllegalArgumentException("pages must be contiguous and belong to contentUnit");
            }
        }
    }
}
