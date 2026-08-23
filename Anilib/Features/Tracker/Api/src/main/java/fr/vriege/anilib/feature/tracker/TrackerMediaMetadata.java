package fr.vriege.anilib.feature.tracker;

import java.net.URI;
import java.util.Objects;
import java.util.Optional;

public record TrackerMediaMetadata(
        Optional<URI> artworkUri,
        Optional<String> format,
        Optional<String> publishingStatus,
        Optional<TrackerAiringSchedule> nextAiring) {
    private static final TrackerMediaMetadata EMPTY = new TrackerMediaMetadata(
            Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());

    public TrackerMediaMetadata {
        artworkUri = Objects.requireNonNull(artworkUri, "artworkUri must not be null");
        format = normalized(format, "format");
        publishingStatus = normalized(publishingStatus, "publishingStatus");
        nextAiring = Objects.requireNonNull(nextAiring, "nextAiring must not be null");
    }

    public static TrackerMediaMetadata empty() {
        return EMPTY;
    }

    private static Optional<String> normalized(Optional<String> value, String name) {
        Optional<String> candidate = Objects.requireNonNull(value, name + " must not be null")
                .map(String::strip);
        if (candidate.isPresent() && candidate.orElseThrow().isEmpty()) {
            throw new IllegalArgumentException(name + " must not contain a blank value");
        }
        return candidate;
    }
}
