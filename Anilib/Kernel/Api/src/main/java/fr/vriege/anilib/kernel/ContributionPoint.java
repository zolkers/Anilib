package fr.vriege.anilib.kernel;

import fr.vriege.anilib.foundation.component.ComponentId;
import fr.vriege.anilib.foundation.validation.Preconditions;

/** A typed, ordered, zero-to-many extension point. */
public record ContributionPoint<T>(ComponentId id, Class<T> type) implements Comparable<ContributionPoint<?>> {
    public ContributionPoint {
        Preconditions.requireNonNull(id, "id");
        Preconditions.requireNonNull(type, "type");
    }

    public static <T> ContributionPoint<T> of(String id, Class<T> type) {
        return new ContributionPoint<>(ComponentId.of(id), type);
    }

    @Override
    public int compareTo(ContributionPoint<?> other) {
        int idComparison = id.compareTo(other.id);
        return idComparison != 0 ? idComparison : type.getName().compareTo(other.type.getName());
    }

    @Override
    public String toString() {
        return id + "<" + type.getSimpleName() + ">";
    }
}
