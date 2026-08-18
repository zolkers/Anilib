package fr.vriege.anilib.feature.source;

import fr.vriege.anilib.foundation.validation.Preconditions;

public record SourceSearchRequest(String query, SourceBrowseRequest browseRequest) {
    public SourceSearchRequest {
        query = Preconditions.requireNonBlank(query, "query").strip();
        Preconditions.requireNonNull(browseRequest, "browseRequest");
    }
}
