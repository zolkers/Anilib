package fr.vriege.anilib.feature.library;

import fr.vriege.anilib.foundation.validation.Preconditions;

import java.time.Instant;

public record LibraryHistoryEntry(String contentId, Instant openedAt, long position) {
    public LibraryHistoryEntry {
        Preconditions.requireNonBlank(contentId, "contentId");
        Preconditions.requireNonNull(openedAt, "openedAt");
        if (position < 0) {
            throw new IllegalArgumentException("position must not be negative");
        }
    }
}
