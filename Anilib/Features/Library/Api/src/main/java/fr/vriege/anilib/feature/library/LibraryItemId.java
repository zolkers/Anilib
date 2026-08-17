package fr.vriege.anilib.feature.library;

import fr.vriege.anilib.foundation.validation.Preconditions;

import java.util.UUID;

/** Stable identity independent of a source extension's mutable metadata. */
public record LibraryItemId(String value) implements Comparable<LibraryItemId> {
    public LibraryItemId {
        Preconditions.requireNonBlank(value, "value");
    }

    public static LibraryItemId create() {
        return new LibraryItemId(UUID.randomUUID().toString());
    }

    @Override
    public int compareTo(LibraryItemId other) {
        return value.compareTo(other.value);
    }

    @Override
    public String toString() {
        return value;
    }
}
