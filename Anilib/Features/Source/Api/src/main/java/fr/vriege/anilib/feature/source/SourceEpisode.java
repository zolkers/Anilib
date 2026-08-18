package fr.vriege.anilib.feature.source;

import fr.vriege.anilib.foundation.validation.Preconditions;

import java.time.Instant;
import java.util.Optional;

/** Immutable episode summary supplied by one streaming source. */
public record SourceEpisode(
        SourceEpisodeId id,
        String title,
        double episodeNumber,
        Optional<Instant> uploadedAt,
        Optional<String> scanlator) {
    public static final double UNKNOWN_NUMBER = -1.0d;

    public SourceEpisode {
        Preconditions.requireNonNull(id, "id");
        title = Preconditions.requireNonBlank(title, "title");
        if (!Double.isFinite(episodeNumber) || episodeNumber < UNKNOWN_NUMBER) {
            throw new IllegalArgumentException("episodeNumber must be finite, non-negative, or unknown");
        }
        uploadedAt = Preconditions.requireNonNull(uploadedAt, "uploadedAt");
        scanlator = Preconditions.requireNonNull(scanlator, "scanlator")
                .map(String::strip)
                .filter(value -> !value.isEmpty());
    }
}
