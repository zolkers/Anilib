package fr.vriege.anilib.feature.updates;

import fr.vriege.anilib.feature.library.LibraryItemId;
import fr.vriege.anilib.foundation.validation.Preconditions;

import java.util.Objects;

/** Actionable per-title failure retained in the latest run snapshot. */
public record LibraryUpdateFailure(LibraryItemId libraryItemId, String title, String message) {
    public LibraryUpdateFailure {
        Objects.requireNonNull(libraryItemId, "libraryItemId must not be null");
        Preconditions.requireNonBlank(title, "title");
        Preconditions.requireNonBlank(message, "message");
    }
}
