package fr.vriege.anilib.foundation.component;

import fr.vriege.anilib.foundation.validation.Preconditions;

import java.util.regex.Pattern;

/** Stable, human-readable identity used by modules, plugins, and capabilities. */
public record ComponentId(String value) implements Comparable<ComponentId> {
    private static final Pattern VALID_ID = Pattern.compile("[a-z][a-z0-9]*(?:[.-][a-z0-9]+)*");

    public ComponentId {
        Preconditions.requireNonBlank(value, "value");
        if (!VALID_ID.matcher(value).matches()) {
            throw new IllegalArgumentException("Invalid component id: " + value);
        }
    }

    public static ComponentId of(String value) {
        return new ComponentId(value);
    }

    @Override
    public int compareTo(ComponentId other) {
        return value.compareTo(other.value);
    }

    @Override
    public String toString() {
        return value;
    }
}

