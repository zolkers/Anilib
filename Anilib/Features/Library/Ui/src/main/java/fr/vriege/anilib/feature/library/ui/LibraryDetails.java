package fr.vriege.anilib.feature.library.ui;

import fr.vriege.anilib.feature.library.LibraryItemId;
import fr.vriege.anilib.feature.library.LibraryProgress;
import fr.vriege.anilib.feature.library.LibraryOrigin;
import fr.vriege.anilib.feature.library.MediaKind;
import fr.vriege.anilib.feature.library.PublicationStatus;

import java.time.Instant;
import java.net.URI;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record LibraryDetails(
        LibraryItemId id,
        String title,
        MediaKind kind,
        Instant addedAt,
        List<String> categories,
        boolean favorite,
        Optional<LibraryProgress> progress,
        String description,
        List<String> authors,
        List<String> artists,
        PublicationStatus publicationStatus,
        Optional<URI> artwork,
        List<String> genres,
        Optional<LibraryOrigin> origin,
        int historyEntryCount) {
    public LibraryDetails {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(title, "title must not be null");
        Objects.requireNonNull(kind, "kind must not be null");
        Objects.requireNonNull(addedAt, "addedAt must not be null");
        categories = List.copyOf(categories);
        Objects.requireNonNull(progress, "progress must not be null");
        Objects.requireNonNull(description, "description must not be null");
        authors = List.copyOf(authors);
        artists = List.copyOf(artists);
        Objects.requireNonNull(publicationStatus, "publicationStatus must not be null");
        Objects.requireNonNull(artwork, "artwork must not be null");
        genres = List.copyOf(genres);
        Objects.requireNonNull(origin, "origin must not be null");
        if (historyEntryCount < 0) {
            throw new IllegalArgumentException("historyEntryCount must not be negative");
        }
    }
}
