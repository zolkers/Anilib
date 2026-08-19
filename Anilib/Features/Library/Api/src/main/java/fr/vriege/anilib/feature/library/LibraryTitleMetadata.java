package fr.vriege.anilib.feature.library;

import fr.vriege.anilib.foundation.validation.Preconditions;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.net.URI;

public record LibraryTitleMetadata(
        String description,
        List<String> authors,
        List<String> artists,
        PublicationStatus publicationStatus,
        Optional<URI> artwork,
        List<String> genres) {

    public LibraryTitleMetadata {
        Preconditions.requireNonNull(description, "description");
        authors = validatedPeople(authors, "authors");
        artists = validatedPeople(artists, "artists");
        Preconditions.requireNonNull(publicationStatus, "publicationStatus");
        artwork = Preconditions.requireNonNull(artwork, "artwork");
        artwork.ifPresent(LibraryTitleMetadata::validateArtwork);
        genres = validatedPeople(genres, "genres");
    }

    public LibraryTitleMetadata(
            String description,
            List<String> authors,
            List<String> artists,
            PublicationStatus publicationStatus) {
        this(description, authors, artists, publicationStatus, Optional.empty(), List.of());
    }

    public static LibraryTitleMetadata empty() {
        return new LibraryTitleMetadata(
                "",
                List.of(),
                List.of(),
                PublicationStatus.UNKNOWN,
                Optional.empty(),
                List.of());
    }

    private static List<String> validatedPeople(List<String> people, String name) {
        Preconditions.requireNonNull(people, name);
        List<String> copy = List.copyOf(people);
        HashSet<String> unique = copy.size() > 1 ? new HashSet<>(copy.size()) : null;
        for (String person : copy) {
            if (person.isBlank()) {
                throw new IllegalArgumentException(name + " must not contain blank values");
            }
            if (unique != null && !unique.add(person)) {
                throw new IllegalArgumentException(name + " must not contain duplicate values");
            }
        }
        return copy;
    }

    private static void validateArtwork(URI value) {
        URI uri = Preconditions.requireNonNull(value, "artwork").normalize();
        String scheme = uri.getScheme();
        if (!uri.isAbsolute() || scheme == null
                || !(scheme.equalsIgnoreCase("http")
                || scheme.equalsIgnoreCase("https")
                || scheme.equalsIgnoreCase("file"))
                || uri.getUserInfo() != null
                || uri.getFragment() != null
                || (!scheme.equalsIgnoreCase("file") && uri.getHost() == null)) {
            throw new IllegalArgumentException("artwork must use an absolute HTTP(S) or file URI");
        }
    }
}
