package fr.vriege.anilib.feature.source;

import fr.vriege.anilib.foundation.validation.Preconditions;

public record SourcePageResource(
        SourceContentUnitId contentUnitId,
        String value,
        int index,
        long estimatedBytes) {
    public static final long UNKNOWN_SIZE = -1L;

    public SourcePageResource {
        Preconditions.requireNonNull(contentUnitId, "contentUnitId");
        value = Preconditions.requireNonBlank(value, "value");
        if (value.length() > 4096) {
            throw new IllegalArgumentException("value must not exceed 4096 characters");
        }
        if (index < 0) {
            throw new IllegalArgumentException("index must not be negative");
        }
        if (estimatedBytes < UNKNOWN_SIZE) {
            throw new IllegalArgumentException("estimatedBytes must be non-negative or unknown");
        }
    }
}
