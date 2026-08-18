package fr.vriege.anilib.kernel;

import fr.vriege.anilib.foundation.component.ComponentId;
import fr.vriege.anilib.foundation.validation.Preconditions;

public record Contribution<T>(ComponentId contributor, int priority, T value) {
    public Contribution {
        Preconditions.requireNonNull(contributor, "contributor");
        Preconditions.requireNonNull(value, "value");
    }
}
