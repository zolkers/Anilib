package fr.vriege.anilib.kernel;

import fr.vriege.anilib.foundation.component.ComponentId;
import fr.vriege.anilib.foundation.validation.Preconditions;

/**
 * An immutable value contributed by a plugin to a typed extension point.
 *
 * <p>The contributor identity makes provenance explicit. Priority controls
 * presentation by {@link StartedAnilib#contributions(ContributionPoint)}:
 * higher values precede lower values, with contributor identity providing a
 * deterministic tie-breaker.</p>
 *
 * @param <T>         the contribution value type
 * @param contributor the component that supplied the value
 * @param priority    the ordering priority; larger values sort first
 * @param value       the contributed value
 */
public record Contribution<T>(ComponentId contributor, int priority, T value) {
    /**
     * Creates a contribution.
     *
     * @throws NullPointerException if {@code contributor} or {@code value} is
     *                              {@code null}
     */
    public Contribution {
        Preconditions.requireNonNull(contributor, "contributor");
        Preconditions.requireNonNull(value, "value");
    }
}
