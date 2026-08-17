package fr.vriege.anilib.feature.downloads;

import fr.vriege.anilib.feature.library.LibraryItemId;
import fr.vriege.anilib.feature.source.SourceCatalogueItemId;
import fr.vriege.anilib.feature.source.SourceContentUnit;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/** Immutable observable state for one queued or stored content unit. */
public record DownloadJobSnapshot(
        DownloadId id,
        LibraryItemId libraryItemId,
        String title,
        SourceCatalogueItemId sourceItemId,
        SourceContentUnit contentUnit,
        DownloadStatus status,
        int completedPages,
        int totalPages,
        long downloadedBytes,
        Optional<String> error,
        Instant updatedAt) {
    public DownloadJobSnapshot {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(libraryItemId, "libraryItemId must not be null");
        Objects.requireNonNull(title, "title must not be null");
        Objects.requireNonNull(sourceItemId, "sourceItemId must not be null");
        Objects.requireNonNull(contentUnit, "contentUnit must not be null");
        Objects.requireNonNull(status, "status must not be null");
        if (totalPages < 1 || completedPages < 0 || completedPages > totalPages) {
            throw new IllegalArgumentException("page progress must address the content unit");
        }
        if (downloadedBytes < 0) {
            throw new IllegalArgumentException("downloadedBytes must not be negative");
        }
        error = error.map(String::strip).filter(message -> !message.isEmpty());
        Objects.requireNonNull(updatedAt, "updatedAt must not be null");
    }

    public double progress() {
        return (double) completedPages / totalPages;
    }
}
