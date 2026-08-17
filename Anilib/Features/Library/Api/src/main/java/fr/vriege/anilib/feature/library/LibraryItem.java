package fr.vriege.anilib.feature.library;

import fr.vriege.anilib.foundation.validation.Preconditions;

import java.time.Instant;
import java.util.Set;

/** Immutable title snapshot owned by the Library feature. */
public record LibraryItem(
        LibraryItemId id,
        String title,
        MediaKind kind,
        Instant addedAt,
        Set<String> categories) {

    public LibraryItem {
        Preconditions.requireNonNull(id, "id");
        Preconditions.requireNonBlank(title, "title");
        Preconditions.requireNonNull(kind, "kind");
        Preconditions.requireNonNull(addedAt, "addedAt");
        categories = Set.copyOf(categories);
        if (categories.stream().anyMatch(String::isBlank)) {
            throw new IllegalArgumentException("categories must not contain blank values");
        }
    }

    public static LibraryItem create(String title, MediaKind kind) {
        return new LibraryItem(LibraryItemId.create(), title, kind, Instant.now(), Set.of());
    }
}
