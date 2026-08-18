package fr.vriege.anilib.feature.updates;

import fr.vriege.anilib.feature.library.LibraryItemId;
import fr.vriege.anilib.foundation.validation.Preconditions;

public record LibraryUpdateEventId(
        LibraryItemId libraryItemId,
        String sourceContentId) {
    public LibraryUpdateEventId {
        Preconditions.requireNonNull(libraryItemId, "libraryItemId");
        sourceContentId = Preconditions.requireNonBlank(sourceContentId, "sourceContentId");
    }
}
