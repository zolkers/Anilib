package fr.vriege.anilib.feature.library;

import fr.vriege.anilib.foundation.validation.Preconditions;

public record LibraryOrigin(String sourceId, String sourceItemKey) {
    public LibraryOrigin {
        sourceId = Preconditions.requireNonBlank(sourceId, "sourceId");
        sourceItemKey = Preconditions.requireNonBlank(sourceItemKey, "sourceItemKey");
    }
}
