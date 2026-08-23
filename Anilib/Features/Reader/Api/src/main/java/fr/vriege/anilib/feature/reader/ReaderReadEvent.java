package fr.vriege.anilib.feature.reader;

import fr.vriege.anilib.feature.library.LibraryItemId;

import java.util.Objects;
import java.util.Set;

public record ReaderReadEvent(
        LibraryItemId libraryItemId,
        Set<String> contentIds,
        boolean read) {
    public ReaderReadEvent {
        Objects.requireNonNull(libraryItemId, "libraryItemId must not be null");
        contentIds = Set.copyOf(Objects.requireNonNull(contentIds, "contentIds must not be null"));
        if (contentIds.isEmpty() || contentIds.stream().anyMatch(String::isBlank)) {
            throw new IllegalArgumentException("contentIds must contain non-blank values");
        }
    }
}
