package fr.vriege.anilib.feature.library.ui;

import fr.vriege.anilib.feature.library.LibraryItemId;

import java.util.Objects;
import java.util.Optional;

public final class LibraryNavigator {
    private LibraryPage page = LibraryPage.LIBRARY;
    private LibraryPage detailsOrigin = LibraryPage.LIBRARY;
    private Optional<LibraryItemId> selectedTitle = Optional.empty();

    public LibraryNavigator() {
    }

    public synchronized LibraryNavigationState state() {
        return new LibraryNavigationState(page, selectedTitle);
    }

    public synchronized void openLibrary() {
        page = LibraryPage.LIBRARY;
        selectedTitle = Optional.empty();
    }

    public synchronized void openHistory() {
        page = LibraryPage.HISTORY;
        selectedTitle = Optional.empty();
    }

    public synchronized void openDetails(LibraryItemId id) {
        if (page != LibraryPage.DETAILS) {
            detailsOrigin = page;
        }
        page = LibraryPage.DETAILS;
        selectedTitle = Optional.of(Objects.requireNonNull(id, "id must not be null"));
    }

    public synchronized void back() {
        if (page != LibraryPage.DETAILS) {
            return;
        }
        page = detailsOrigin;
        selectedTitle = Optional.empty();
    }
}
