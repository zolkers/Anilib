package fr.vriege.anilib.feature.localsource;

import fr.vriege.anilib.foundation.validation.Preconditions;

public record LocalPage(
        LocalPublicationId publicationId,
        String entryName,
        int index,
        long size) {

    public LocalPage {
        Preconditions.requireNonNull(publicationId, "publicationId");
        LocalPublicationId.validateRelativePath(entryName, "entryName");
        if (index < 0) {
            throw new IllegalArgumentException("index must not be negative");
        }
        if (size < -1) {
            throw new IllegalArgumentException("size must be non-negative or unknown");
        }
    }
}
