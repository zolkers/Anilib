package fr.vriege.anilib.feature.library;

import fr.vriege.anilib.foundation.validation.Preconditions;

import java.time.Instant;
import java.util.OptionalDouble;

/** Latest reader or player position for one chapter, episode, or local unit. */
public record LibraryProgress(
        String contentId,
        long position,
        long extent,
        Instant updatedAt) {

    public static final long UNKNOWN_EXTENT = -1L;

    public LibraryProgress {
        Preconditions.requireNonBlank(contentId, "contentId");
        if (position < 0) {
            throw new IllegalArgumentException("position must not be negative");
        }
        if (extent < UNKNOWN_EXTENT) {
            throw new IllegalArgumentException("extent must be non-negative or UNKNOWN_EXTENT");
        }
        if (extent != UNKNOWN_EXTENT && position > extent) {
            throw new IllegalArgumentException("position must not exceed extent");
        }
        Preconditions.requireNonNull(updatedAt, "updatedAt");
    }

    public OptionalDouble completion() {
        if (extent <= 0) {
            return OptionalDouble.empty();
        }
        return OptionalDouble.of((double) position / extent);
    }
}
