package fr.vriege.anilib.feature.library.ui;

import java.util.List;

public record LibraryOverview(
        List<LibraryCard> titles,
        List<String> categories,
        int favoriteCount) {
    public LibraryOverview {
        titles = List.copyOf(titles);
        categories = List.copyOf(categories);
        if (favoriteCount < 0 || favoriteCount > titles.size()) {
            throw new IllegalArgumentException("favoriteCount must match the title population");
        }
    }
}
