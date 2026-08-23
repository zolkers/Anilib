package fr.vriege.anilib.feature.player;

import fr.vriege.anilib.feature.library.LibraryItemId;
import fr.vriege.anilib.feature.source.SourceEpisodeId;

import java.util.Objects;

public record PlayerProgressEvent(
        LibraryItemId libraryItemId,
        SourceEpisodeId episodeId,
        double episodeNumber) {
    public PlayerProgressEvent {
        Objects.requireNonNull(libraryItemId, "libraryItemId must not be null");
        Objects.requireNonNull(episodeId, "episodeId must not be null");
        if (!Double.isFinite(episodeNumber) || episodeNumber < 0.0d) {
            throw new IllegalArgumentException("episodeNumber must be finite and non-negative");
        }
    }
}
