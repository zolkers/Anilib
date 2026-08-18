package fr.vriege.anilib.feature.updates;

import fr.vriege.anilib.feature.library.LibraryItemId;
import fr.vriege.anilib.feature.library.MediaKind;
import fr.vriege.anilib.foundation.validation.Preconditions;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public record LibraryUpdateEvent(
        LibraryItemId libraryItemId,
        String libraryTitle,
        MediaKind kind,
        String sourceContentId,
        String contentTitle,
        Optional<Instant> publishedAt,
        Instant discoveredAt,
        boolean read) {
    public LibraryUpdateEvent {
        Objects.requireNonNull(libraryItemId, "libraryItemId must not be null");
        Preconditions.requireNonBlank(libraryTitle, "libraryTitle");
        Objects.requireNonNull(kind, "kind must not be null");
        Preconditions.requireNonBlank(sourceContentId, "sourceContentId");
        Preconditions.requireNonBlank(contentTitle, "contentTitle");
        publishedAt = Objects.requireNonNull(publishedAt, "publishedAt must not be null");
        Objects.requireNonNull(discoveredAt, "discoveredAt must not be null");
    }

    public LibraryUpdateEvent markRead() {
        return read ? this : new LibraryUpdateEvent(
                libraryItemId,
                libraryTitle,
                kind,
                sourceContentId,
                contentTitle,
                publishedAt,
                discoveredAt,
                true);
    }
}
