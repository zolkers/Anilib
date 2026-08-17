package fr.vriege.anilib.feature.library.ui;

import fr.vriege.anilib.feature.library.LibraryItemId;

import java.util.Objects;
import java.util.Optional;

/** Immutable current destination for a Library platform renderer. */
public record LibraryNavigationState(LibraryPage page, Optional<LibraryItemId> selectedTitle) {
    public LibraryNavigationState {
        Objects.requireNonNull(page, "page must not be null");
        Objects.requireNonNull(selectedTitle, "selectedTitle must not be null");
        if ((page == LibraryPage.DETAILS) != selectedTitle.isPresent()) {
            throw new IllegalArgumentException("only the details page must select a title");
        }
    }
}
