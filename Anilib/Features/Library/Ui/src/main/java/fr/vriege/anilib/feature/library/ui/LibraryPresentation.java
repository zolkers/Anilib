package fr.vriege.anilib.feature.library.ui;

import fr.vriege.anilib.feature.library.LibraryItemId;

import java.util.Optional;

/** Platform-neutral presentation snapshots for the Library feature. */
public interface LibraryPresentation {
    LibraryOverview library();

    Optional<LibraryDetails> details(LibraryItemId id);

    LibraryHistory history();
}
