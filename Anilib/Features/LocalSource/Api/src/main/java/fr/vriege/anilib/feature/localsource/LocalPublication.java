package fr.vriege.anilib.feature.localsource;

import fr.vriege.anilib.foundation.validation.Preconditions;

public record LocalPublication(LocalPublicationId id, String title) {
    public LocalPublication {
        Preconditions.requireNonNull(id, "id");
        Preconditions.requireNonBlank(title, "title");
    }
}
