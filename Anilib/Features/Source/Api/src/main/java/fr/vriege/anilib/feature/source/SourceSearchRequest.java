package fr.vriege.anilib.feature.source;

import fr.vriege.anilib.foundation.validation.Preconditions;

public record SourceSearchRequest(String query, SourceBrowseRequest browseRequest) {
    public SourceSearchRequest {
        query = Preconditions.requireNonNull(query, "query").strip();
        browseRequest = Preconditions.requireNonNull(browseRequest, "browseRequest");
        if (query.isBlank() && browseRequest.filters().isEmpty()) {
            throw new IllegalArgumentException("query or filters must be provided");
        }
    }
}
