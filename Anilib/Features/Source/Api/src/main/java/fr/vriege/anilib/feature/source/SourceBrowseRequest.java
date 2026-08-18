package fr.vriege.anilib.feature.source;

import fr.vriege.anilib.foundation.validation.Preconditions;

import java.util.List;
import java.util.Map;

public record SourceBrowseRequest(
        int page,
        int pageSize,
        List<SourceFilterValue> filters,
        Map<String, String> preferences) {
    public SourceBrowseRequest {
        if (page < 1) {
            throw new IllegalArgumentException("page must be at least one");
        }
        if (pageSize < 1 || pageSize > 100) {
            throw new IllegalArgumentException("pageSize must be between 1 and 100");
        }
        filters = List.copyOf(Preconditions.requireNonNull(filters, "filters"));
        preferences = Map.copyOf(Preconditions.requireNonNull(preferences, "preferences"));
        if (preferences.entrySet().stream()
                .anyMatch(entry -> entry.getKey().isBlank() || entry.getValue() == null)) {
            throw new IllegalArgumentException("preferences must use non-blank keys and non-null values");
        }
    }
}
