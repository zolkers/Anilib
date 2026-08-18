package fr.vriege.anilib.feature.discovery;

import fr.vriege.anilib.feature.source.SourcePreferenceDefinition;
import fr.vriege.anilib.foundation.validation.Preconditions;

public record SourcePreferenceSnapshot(SourcePreferenceDefinition definition, String value) {
    public SourcePreferenceSnapshot {
        Preconditions.requireNonNull(definition, "definition");
        value = Preconditions.requireNonNull(value, "value");
    }
}
