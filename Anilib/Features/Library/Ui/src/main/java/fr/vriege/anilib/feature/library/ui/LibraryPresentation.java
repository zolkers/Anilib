package fr.vriege.anilib.feature.library.ui;

import fr.vriege.anilib.feature.library.LibraryItemId;

import java.util.Optional;

public interface LibraryPresentation {
    LibraryOverview library();

    Optional<LibraryDetails> details(LibraryItemId id);

    LibraryHistory history();
}
