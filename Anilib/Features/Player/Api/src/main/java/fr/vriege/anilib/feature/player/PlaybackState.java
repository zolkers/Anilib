package fr.vriege.anilib.feature.player;

import fr.vriege.anilib.feature.library.LibraryItemId;
import fr.vriege.anilib.feature.source.SourceEpisodeId;

import java.time.Instant;
import java.util.Objects;
import java.util.OptionalDouble;

public record PlaybackState(
        LibraryItemId libraryItemId,
        SourceEpisodeId episodeId,
        long positionMillis,
        long durationMillis,
        boolean completed,
        Instant updatedAt) {
    public static final long UNKNOWN_DURATION = -1L;

    public PlaybackState {
        Objects.requireNonNull(libraryItemId, "libraryItemId must not be null");
        Objects.requireNonNull(episodeId, "episodeId must not be null");
        if (positionMillis < 0) {
            throw new IllegalArgumentException("positionMillis must not be negative");
        }
        if (durationMillis < UNKNOWN_DURATION) {
            throw new IllegalArgumentException("durationMillis must be non-negative or unknown");
        }
        if (durationMillis != UNKNOWN_DURATION && positionMillis > durationMillis) {
            throw new IllegalArgumentException("positionMillis must not exceed durationMillis");
        }
        Objects.requireNonNull(updatedAt, "updatedAt must not be null");
    }

    public OptionalDouble completion() {
        if (durationMillis <= 0) {
            return OptionalDouble.empty();
        }
        return OptionalDouble.of((double) positionMillis / durationMillis);
    }
}
