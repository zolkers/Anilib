package fr.vriege.anilib.feature.tracker;

import java.time.Instant;
import java.util.Objects;

public record TrackerAiringSchedule(long episode, Instant airingAt) {
    public TrackerAiringSchedule {
        if (episode < 1L) {
            throw new IllegalArgumentException("episode must be positive");
        }
        Objects.requireNonNull(airingAt, "airingAt must not be null");
    }
}
