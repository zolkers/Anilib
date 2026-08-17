package fr.vriege.anilib.feature.source;

import fr.vriege.anilib.foundation.validation.Preconditions;

/** Stable source-scoped identity for one remote or local catalogue title. */
public record SourceCatalogueItemId(SourceId sourceId, String value) implements Comparable<SourceCatalogueItemId> {
    public SourceCatalogueItemId {
        Preconditions.requireNonNull(sourceId, "sourceId");
        value = Preconditions.requireNonBlank(value, "value");
        if (value.length() > 2048) {
            throw new IllegalArgumentException("value must not exceed 2048 characters");
        }
    }

    @Override
    public int compareTo(SourceCatalogueItemId other) {
        int sourceOrder = sourceId.compareTo(other.sourceId);
        return sourceOrder != 0 ? sourceOrder : value.compareTo(other.value);
    }
}
