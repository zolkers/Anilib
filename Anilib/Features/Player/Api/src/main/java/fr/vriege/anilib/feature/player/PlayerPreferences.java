package fr.vriege.anilib.feature.player;

import java.util.Objects;
import java.util.Optional;

public record PlayerPreferences(
        PlayerDecoderPolicy decoderPolicy,
        Optional<String> preferredAudioLanguage,
        PlayerSubtitlePolicy subtitlePolicy,
        Optional<String> preferredSubtitleLanguage,
        PlayerQualityPolicy qualityPolicy,
        Optional<String> preferredQuality,
        long introEndMillis,
        long outroDurationMillis,
        int completionThresholdPercent) {
    private static final long MAXIMUM_SKIP_MILLIS = 30L * 60L * 1000L;

    public PlayerPreferences {
        Objects.requireNonNull(decoderPolicy, "decoderPolicy must not be null");
        preferredAudioLanguage = normalized(preferredAudioLanguage, "preferredAudioLanguage");
        Objects.requireNonNull(subtitlePolicy, "subtitlePolicy must not be null");
        preferredSubtitleLanguage = normalized(
                preferredSubtitleLanguage,
                "preferredSubtitleLanguage");
        Objects.requireNonNull(qualityPolicy, "qualityPolicy must not be null");
        preferredQuality = normalized(preferredQuality, "preferredQuality");
        if (qualityPolicy == PlayerQualityPolicy.PREFERRED && preferredQuality.isEmpty()) {
            throw new IllegalArgumentException("preferredQuality is required for PREFERRED quality policy");
        }
        if (introEndMillis < 0 || introEndMillis > MAXIMUM_SKIP_MILLIS) {
            throw new IllegalArgumentException("introEndMillis must be between zero and 30 minutes");
        }
        if (outroDurationMillis < 0 || outroDurationMillis > MAXIMUM_SKIP_MILLIS) {
            throw new IllegalArgumentException("outroDurationMillis must be between zero and 30 minutes");
        }
        if (completionThresholdPercent < 1 || completionThresholdPercent > 100) {
            throw new IllegalArgumentException("completionThresholdPercent must be between one and 100");
        }
    }

    public static PlayerPreferences defaults() {
        return new PlayerPreferences(
                PlayerDecoderPolicy.AUTOMATIC,
                Optional.empty(),
                PlayerSubtitlePolicy.OFF,
                Optional.empty(),
                PlayerQualityPolicy.AUTOMATIC,
                Optional.empty(),
                0L,
                0L,
                85);
    }

    private static Optional<String> normalized(Optional<String> value, String name) {
        return Objects.requireNonNull(value, name + " must not be null")
                .map(String::strip)
                .filter(candidate -> !candidate.isEmpty());
    }
}
