package fr.vriege.anilib.feature.player;

import fr.vriege.anilib.feature.source.SourceVideoStream;

import java.util.Objects;
import java.util.Optional;

public record PlayerMedia(
        String title,
        SourceVideoStream stream,
        Optional<String> subtitleId,
        long startPositionMillis) {
    public PlayerMedia {
        if (Objects.requireNonNull(title, "title must not be null").isBlank()) {
            throw new IllegalArgumentException("title must not be blank");
        }
        Objects.requireNonNull(stream, "stream must not be null");
        Optional<String> requestedSubtitle = Objects.requireNonNull(
                subtitleId,
                "subtitleId must not be null").map(String::strip).filter(value -> !value.isEmpty());
        if (requestedSubtitle.isPresent() && stream.subtitles().stream()
                .noneMatch(track -> track.id().equals(requestedSubtitle.orElseThrow()))) {
            throw new IllegalArgumentException("subtitleId must reference the selected stream");
        }
        subtitleId = requestedSubtitle;
        if (startPositionMillis < 0) {
            throw new IllegalArgumentException("startPositionMillis must not be negative");
        }
    }
}
