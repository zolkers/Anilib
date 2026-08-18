package fr.vriege.anilib.feature.source;

import fr.vriege.anilib.foundation.validation.Preconditions;

public record SourceEpisodeId(SourceCatalogueItemId itemId, String value)
        implements Comparable<SourceEpisodeId> {
    public SourceEpisodeId {
        Preconditions.requireNonNull(itemId, "itemId");
        value = Preconditions.requireNonBlank(value, "value");
        if (value.length() > 2048) {
            throw new IllegalArgumentException("value must not exceed 2048 characters");
        }
    }

    @Override
    public int compareTo(SourceEpisodeId other) {
        int itemOrder = itemId.compareTo(other.itemId);
        return itemOrder != 0 ? itemOrder : value.compareTo(other.value);
    }
}
