package fr.vriege.anilib.feature.source;

import fr.vriege.anilib.foundation.validation.Preconditions;

import java.time.Instant;
import java.util.Optional;

/** Immutable chapter summary ordered by the owning source. */
public record SourceContentUnit(
        SourceContentUnitId id,
        String title,
        Optional<Instant> publishedAt) {
    public SourceContentUnit {
        Preconditions.requireNonNull(id, "id");
        title = Preconditions.requireNonBlank(title, "title");
        publishedAt = Preconditions.requireNonNull(publishedAt, "publishedAt");
    }
}
