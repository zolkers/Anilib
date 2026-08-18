package fr.vriege.anilib.feature.player;

import java.util.Objects;
import java.util.Optional;

/** Current UI-neutral state reported by a platform media engine. */
public record PlayerPlaybackSnapshot(
        PlayerPlaybackStatus status,
        long positionMillis,
        long durationMillis,
        float volume,
        float playbackSpeed,
        Optional<String> errorMessage) {
    public PlayerPlaybackSnapshot {
        Objects.requireNonNull(status, "status must not be null");
        if (positionMillis < 0) {
            throw new IllegalArgumentException("positionMillis must not be negative");
        }
        if (durationMillis < PlaybackState.UNKNOWN_DURATION) {
            throw new IllegalArgumentException("durationMillis is invalid");
        }
        if (!Float.isFinite(volume) || volume < 0.0f || volume > 1.0f) {
            throw new IllegalArgumentException("volume must be between zero and one");
        }
        if (!Float.isFinite(playbackSpeed) || playbackSpeed < 0.25f || playbackSpeed > 2.0f) {
            throw new IllegalArgumentException("playbackSpeed must be between 0.25 and 2.0");
        }
        errorMessage = Objects.requireNonNull(
                errorMessage,
                "errorMessage must not be null").map(String::strip).filter(value -> !value.isEmpty());
        if (status == PlayerPlaybackStatus.FAILED && errorMessage.isEmpty()) {
            throw new IllegalArgumentException("failed playback must expose an error message");
        }
    }

    public static PlayerPlaybackSnapshot unavailable() {
        return new PlayerPlaybackSnapshot(
                PlayerPlaybackStatus.UNAVAILABLE,
                0L,
                PlaybackState.UNKNOWN_DURATION,
                1.0f,
                1.0f,
                Optional.empty());
    }
}
