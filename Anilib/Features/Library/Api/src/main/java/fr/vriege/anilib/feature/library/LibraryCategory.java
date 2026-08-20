package fr.vriege.anilib.feature.library;

import java.util.Objects;

public record LibraryCategory(
        String name,
        LibraryCategoryScope scope,
        LibraryDisplayMode displayMode,
        LibraryDisplayDensity density,
        LibrarySort sort,
        LibraryCategoryUpdatePolicy updatePolicy) {
    private static final int MAXIMUM_NAME_LENGTH = 200;

    public LibraryCategory {
        name = validateName(name, "name");
        Objects.requireNonNull(scope, "scope must not be null");
        Objects.requireNonNull(displayMode, "displayMode must not be null");
        Objects.requireNonNull(density, "density must not be null");
        Objects.requireNonNull(sort, "sort must not be null");
        Objects.requireNonNull(updatePolicy, "updatePolicy must not be null");
    }

    public static LibraryCategory defaults(
            String name,
            LibraryCategoryScope scope,
            LibraryDisplayPreferences preferences) {
        Objects.requireNonNull(preferences, "preferences must not be null");
        return new LibraryCategory(
                name,
                scope,
                preferences.mode(),
                preferences.density(),
                preferences.sort(),
                LibraryCategoryUpdatePolicy.DEFAULT);
    }

    static String validateName(String value, String label) {
        Objects.requireNonNull(value, label + " must not be null");
        if (value.isBlank() || !value.equals(value.strip()) || value.length() > MAXIMUM_NAME_LENGTH) {
            throw new IllegalArgumentException(label + " must be trimmed and contain 1 to 200 characters");
        }
        return value;
    }
}
