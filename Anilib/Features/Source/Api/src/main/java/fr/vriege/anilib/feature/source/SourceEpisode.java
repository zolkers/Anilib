package fr.vriege.anilib.feature.source;

import fr.vriege.anilib.foundation.validation.Preconditions;

import java.net.URI;
import java.time.Instant;
import java.util.Optional;

public record SourceEpisode(
        SourceEpisodeId id,
        String title,
        double episodeNumber,
        Optional<Instant> uploadedAt,
        Optional<String> scanlator,
        Optional<URI> thumbnail) {
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
        thumbnail = Preconditions.requireNonNull(thumbnail, "thumbnail");
        thumbnail.ifPresent(SourceEpisode::validateThumbnail);
    }

    public SourceEpisode(
            SourceEpisodeId id,
            String title,
            double episodeNumber,
            Optional<Instant> uploadedAt,
            Optional<String> scanlator) {
        this(id, title, episodeNumber, uploadedAt, scanlator, Optional.empty());
    }

    private static void validateThumbnail(URI uri) {
        String scheme = uri.getScheme();
        if (scheme == null || !(scheme.equalsIgnoreCase("http")
                || scheme.equalsIgnoreCase("https")
                || scheme.equalsIgnoreCase("file"))) {
            throw new IllegalArgumentException("thumbnail must use http, https, or file");
        }
    }
}
