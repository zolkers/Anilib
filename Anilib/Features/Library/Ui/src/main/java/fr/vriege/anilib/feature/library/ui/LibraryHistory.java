package fr.vriege.anilib.feature.library.ui;

import java.util.List;

/** Reverse-chronological global history presentation snapshot. */
public record LibraryHistory(List<LibraryHistoryRow> entries) {
    public LibraryHistory {
        entries = List.copyOf(entries);
    }
}
