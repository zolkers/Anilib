package fr.vriege.anilib.feature.updates;

import fr.vriege.anilib.feature.library.LibraryItemId;
import fr.vriege.anilib.foundation.validation.Preconditions;

public record LibraryUpdateSkip(
        LibraryItemId libraryItemId,
        String title,
        LibraryUpdateSkipReason reason) {
    public LibraryUpdateSkip {
        Preconditions.requireNonNull(libraryItemId, "libraryItemId");
        title = Preconditions.requireNonBlank(title, "title");
        Preconditions.requireNonNull(reason, "reason");
    }
}
