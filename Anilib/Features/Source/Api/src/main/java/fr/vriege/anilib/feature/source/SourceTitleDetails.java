package fr.vriege.anilib.feature.source;

import java.net.URI;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record SourceTitleDetails(
        SourceCatalogueItemId id,
        String title,
        String description,
        List<String> authors,
        List<String> artists,
        List<String> genres,
        SourcePublicationStatus status,
        Optional<URI> thumbnail,
        SourceContentKind contentKind) {
    public SourceTitleDetails {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(title, "title must not be null");
        Objects.requireNonNull(description, "description must not be null");
        authors = List.copyOf(authors);
        artists = List.copyOf(artists);
        genres = List.copyOf(genres);
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(thumbnail, "thumbnail must not be null");
        Objects.requireNonNull(contentKind, "contentKind must not be null");
    }
}
