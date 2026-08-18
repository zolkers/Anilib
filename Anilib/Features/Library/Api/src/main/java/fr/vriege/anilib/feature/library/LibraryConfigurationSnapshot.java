package fr.vriege.anilib.feature.library;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public record LibraryConfigurationSnapshot(
        LibraryDisplayPreferences displayPreferences,
        List<LibraryCategory> categories) {
    public LibraryConfigurationSnapshot {
        Objects.requireNonNull(displayPreferences, "displayPreferences must not be null");
        categories = List.copyOf(categories);
        Set<String> names = new HashSet<>();
        for (LibraryCategory category : categories) {
            Objects.requireNonNull(category, "categories must not contain null values");
            if (!names.add(category.name())) {
                throw new IllegalArgumentException("categories must have unique names");
            }
        }
        displayPreferences.defaultCategory().ifPresent(category -> {
            if (!names.contains(category)) {
                throw new IllegalArgumentException("defaultCategory must name a configured category");
            }
        });
    }

    public static LibraryConfigurationSnapshot defaults() {
        return new LibraryConfigurationSnapshot(LibraryDisplayPreferences.defaults(), List.of());
    }
}
