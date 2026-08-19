package fr.vriege.anilib.feature.library.ui;

import fr.vriege.anilib.feature.library.LibraryItemId;
import fr.vriege.anilib.feature.library.LibraryProgress;
import fr.vriege.anilib.feature.library.MediaKind;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.net.URI;

public record LibraryCard(
        LibraryItemId id,
        String title,
        MediaKind kind,
        Instant addedAt,
        List<String> categories,
        boolean favorite,
        Optional<LibraryProgress> progress,
        Optional<URI> artwork) {
    public LibraryCard {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(title, "title must not be null");
        Objects.requireNonNull(kind, "kind must not be null");
        Objects.requireNonNull(addedAt, "addedAt must not be null");
        categories = List.copyOf(categories);
        Objects.requireNonNull(progress, "progress must not be null");
        Objects.requireNonNull(artwork, "artwork must not be null");
    }
}
