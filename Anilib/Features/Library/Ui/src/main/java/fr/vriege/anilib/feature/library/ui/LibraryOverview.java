package fr.vriege.anilib.feature.library.ui;

import fr.vriege.anilib.feature.library.LibraryCategory;
import fr.vriege.anilib.feature.library.LibraryDisplayPreferences;

import java.util.List;
import java.util.Objects;

public record LibraryOverview(
        List<LibraryCard> titles,
        List<String> categories,
        List<LibraryCategory> categoryConfigurations,
        LibraryDisplayPreferences displayPreferences,
        int favoriteCount) {
    public LibraryOverview {
        titles = List.copyOf(titles);
        categories = List.copyOf(categories);
        categoryConfigurations = List.copyOf(categoryConfigurations);
        Objects.requireNonNull(displayPreferences, "displayPreferences must not be null");
        if (!categoryConfigurations.stream().map(LibraryCategory::name).toList().equals(categories)) {
            throw new IllegalArgumentException("categoryConfigurations must match categories");
        }
        if (favoriteCount < 0 || favoriteCount > titles.size()) {
            throw new IllegalArgumentException("favoriteCount must match the title population");
        }
    }
}
