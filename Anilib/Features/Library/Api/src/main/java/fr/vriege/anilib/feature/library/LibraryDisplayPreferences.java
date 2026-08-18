package fr.vriege.anilib.feature.library;

import java.util.Objects;
import java.util.Optional;

public record LibraryDisplayPreferences(
        LibraryDisplayMode mode,
        LibraryDisplayDensity density,
        LibrarySort sort,
        Optional<String> defaultCategory) {
    public LibraryDisplayPreferences {
        Objects.requireNonNull(mode, "mode must not be null");
        Objects.requireNonNull(density, "density must not be null");
        Objects.requireNonNull(sort, "sort must not be null");
        defaultCategory = Objects.requireNonNull(defaultCategory, "defaultCategory must not be null");
        defaultCategory.ifPresent(value -> LibraryCategory.validateName(value, "defaultCategory"));
    }

    public static LibraryDisplayPreferences defaults() {
        return new LibraryDisplayPreferences(
                LibraryDisplayMode.GRID,
                LibraryDisplayDensity.COMFORTABLE,
                LibrarySort.TITLE_ASCENDING,
                Optional.empty());
    }
}
