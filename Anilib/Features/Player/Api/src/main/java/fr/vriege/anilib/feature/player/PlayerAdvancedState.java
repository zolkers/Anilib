package fr.vriege.anilib.feature.player;

import java.util.Objects;
import java.util.Optional;

public record PlayerAdvancedState(
        boolean loop,
        long audioDelayMillis,
        long subtitleDelayMillis,
        Optional<String> aspectRatio,
        boolean deinterlace) {
    private static final long MAXIMUM_DELAY_MILLIS = 10L * 60L * 1000L;

    public PlayerAdvancedState {
        if (audioDelayMillis < -MAXIMUM_DELAY_MILLIS || audioDelayMillis > MAXIMUM_DELAY_MILLIS) {
            throw new IllegalArgumentException("audioDelayMillis must be within ten minutes");
        }
        if (subtitleDelayMillis < -MAXIMUM_DELAY_MILLIS
                || subtitleDelayMillis > MAXIMUM_DELAY_MILLIS) {
            throw new IllegalArgumentException("subtitleDelayMillis must be within ten minutes");
        }
        aspectRatio = Objects.requireNonNull(aspectRatio, "aspectRatio must not be null")
                .map(String::strip)
                .filter(value -> !value.isEmpty());
    }

    public static PlayerAdvancedState defaults() {
        return new PlayerAdvancedState(false, 0L, 0L, Optional.empty(), false);
    }
}
