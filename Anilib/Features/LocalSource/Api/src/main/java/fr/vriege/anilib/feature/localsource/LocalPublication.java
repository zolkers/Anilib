package fr.vriege.anilib.feature.localsource;

import fr.vriege.anilib.foundation.validation.Preconditions;

/** One folder or ZIP-compatible archive exposed as a local title. */
public record LocalPublication(LocalPublicationId id, String title) {
    public LocalPublication {
        Preconditions.requireNonNull(id, "id");
        Preconditions.requireNonBlank(title, "title");
    }
}
