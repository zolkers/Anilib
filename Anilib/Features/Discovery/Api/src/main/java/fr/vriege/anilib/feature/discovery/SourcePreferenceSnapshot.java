package fr.vriege.anilib.feature.discovery;

import fr.vriege.anilib.feature.source.SourcePreferenceDefinition;
import fr.vriege.anilib.foundation.validation.Preconditions;

/** Current product-owned value for one source-defined preference. */
public record SourcePreferenceSnapshot(SourcePreferenceDefinition definition, String value) {
    public SourcePreferenceSnapshot {
        Preconditions.requireNonNull(definition, "definition");
        value = Preconditions.requireNonNull(value, "value");
    }
}
