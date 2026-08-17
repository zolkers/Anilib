package fr.vriege.anilib.feature.source;

import fr.vriege.anilib.foundation.component.ComponentId;
import fr.vriege.anilib.foundation.validation.Preconditions;

/** Stable source identity that survives display-name and implementation changes. */
public record SourceId(ComponentId value) implements Comparable<SourceId> {
    public SourceId {
        Preconditions.requireNonNull(value, "value");
    }

    public static SourceId of(String value) {
        return new SourceId(ComponentId.of(value));
    }

    @Override
    public int compareTo(SourceId other) {
        return value.compareTo(other.value);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
