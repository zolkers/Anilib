package fr.vriege.anilib.kernel;

import fr.vriege.anilib.foundation.component.ComponentId;
import fr.vriege.anilib.foundation.validation.Preconditions;

/** A typed one-provider dependency in the immutable plugin graph. */
public record CapabilityKey<T>(ComponentId id, Class<T> type) implements Comparable<CapabilityKey<?>> {
    public CapabilityKey {
        Preconditions.requireNonNull(id, "id");
        Preconditions.requireNonNull(type, "type");
    }

    public static <T> CapabilityKey<T> of(String id, Class<T> type) {
        return new CapabilityKey<>(ComponentId.of(id), type);
    }

    @Override
    public int compareTo(CapabilityKey<?> other) {
        int idComparison = id.compareTo(other.id);
        return idComparison != 0 ? idComparison : type.getName().compareTo(other.type.getName());
    }

    @Override
    public String toString() {
        return id + "<" + type.getSimpleName() + ">";
    }
}
