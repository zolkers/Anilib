package fr.vriege.anilib.feature.library;

import fr.vriege.anilib.foundation.validation.Preconditions;

import java.util.HashSet;
import java.util.List;

/** Editable source-neutral metadata attached to one library title. */
public record LibraryTitleMetadata(
        String description,
        List<String> authors,
        List<String> artists,
        PublicationStatus publicationStatus) {

    public LibraryTitleMetadata {
        Preconditions.requireNonNull(description, "description");
        authors = validatedPeople(authors, "authors");
        artists = validatedPeople(artists, "artists");
        Preconditions.requireNonNull(publicationStatus, "publicationStatus");
    }

    public static LibraryTitleMetadata empty() {
        return new LibraryTitleMetadata("", List.of(), List.of(), PublicationStatus.UNKNOWN);
    }

    private static List<String> validatedPeople(List<String> people, String name) {
        Preconditions.requireNonNull(people, name);
        List<String> copy = List.copyOf(people);
        if (copy.stream().anyMatch(String::isBlank)) {
            throw new IllegalArgumentException(name + " must not contain blank values");
        }
        if (new HashSet<>(copy).size() != copy.size()) {
            throw new IllegalArgumentException(name + " must not contain duplicate values");
        }
        return copy;
    }
}
