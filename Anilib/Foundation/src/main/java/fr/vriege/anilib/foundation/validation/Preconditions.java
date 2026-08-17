package fr.vriege.anilib.foundation.validation;

import java.util.Objects;

/** Shared validation primitives for immutable public models. */
public final class Preconditions {
    private Preconditions() {
    }

    public static <T> T requireNonNull(T value, String name) {
        return Objects.requireNonNull(value, name + " must not be null");
    }

    public static String requireNonBlank(String value, String name) {
        requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}

