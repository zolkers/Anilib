package fr.vriege.anilib.feature.library.ui;

import java.util.List;

public record LibraryHistory(List<LibraryHistoryRow> entries) {
    public LibraryHistory {
        entries = List.copyOf(entries);
    }
}
