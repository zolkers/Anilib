package fr.vriege.anilib.feature.source;

import fr.vriege.anilib.foundation.validation.Preconditions;

public record SourceFilterValue(String filterId, String value) {
    public SourceFilterValue {
        filterId = Preconditions.requireNonBlank(filterId, "filterId");
        value = Preconditions.requireNonNull(value, "value");
    }
}
