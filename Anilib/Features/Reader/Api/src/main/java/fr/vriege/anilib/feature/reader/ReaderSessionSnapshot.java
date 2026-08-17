package fr.vriege.anilib.feature.reader;

import fr.vriege.anilib.feature.library.LibraryItemId;
import fr.vriege.anilib.feature.source.SourceContentUnit;

import java.util.Objects;

/** Immutable observable reader position used by every platform UI. */
public record ReaderSessionSnapshot(
        LibraryItemId libraryItemId,
        String title,
        SourceContentUnit contentUnit,
        int currentPageIndex,
        int pageCount,
        ReadingDirection direction) {
    public ReaderSessionSnapshot {
        Objects.requireNonNull(libraryItemId, "libraryItemId must not be null");
        Objects.requireNonNull(title, "title must not be null");
        Objects.requireNonNull(contentUnit, "contentUnit must not be null");
        Objects.requireNonNull(direction, "direction must not be null");
        if (pageCount < 1) {
            throw new IllegalArgumentException("pageCount must be positive");
        }
        if (currentPageIndex < 0 || currentPageIndex >= pageCount) {
            throw new IllegalArgumentException("currentPageIndex must address an existing page");
        }
    }
}
