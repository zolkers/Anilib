package fr.vriege.anilib.feature.source;

import fr.vriege.anilib.foundation.validation.Preconditions;

import java.time.Instant;
import java.util.Optional;

public record SourceContentUnit(
        SourceContentUnitId id,
        String title,
        double number,
        Optional<Instant> publishedAt) {
    public static final double UNKNOWN_NUMBER = -1.0d;

    public SourceContentUnit {
        Preconditions.requireNonNull(id, "id");
        title = Preconditions.requireNonBlank(title, "title");
        if (!Double.isFinite(number) || number < UNKNOWN_NUMBER) {
            throw new IllegalArgumentException("number must be finite, non-negative, or unknown");
        }
        publishedAt = Preconditions.requireNonNull(publishedAt, "publishedAt");
    }

    public SourceContentUnit(
            SourceContentUnitId id,
            String title,
            Optional<Instant> publishedAt) {
        this(id, title, UNKNOWN_NUMBER, publishedAt);
    }
}
