package fr.vriege.anilib.feature.library.ui;

import fr.vriege.anilib.feature.library.LibraryItemId;

import java.time.Instant;
import java.util.Objects;

public record LibraryHistoryRow(
        LibraryItemId libraryItemId,
        String title,
        String contentId,
        Instant openedAt,
        long position) {
    public LibraryHistoryRow {
        Objects.requireNonNull(libraryItemId, "libraryItemId must not be null");
        Objects.requireNonNull(title, "title must not be null");
        Objects.requireNonNull(contentId, "contentId must not be null");
        Objects.requireNonNull(openedAt, "openedAt must not be null");
        if (position < 0) {
            throw new IllegalArgumentException("position must not be negative");
        }
    }
}
