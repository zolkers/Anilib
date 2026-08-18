package fr.vriege.anilib.feature.downloads;

import fr.vriege.anilib.feature.library.LibraryItemId;
import fr.vriege.anilib.feature.source.SourceCatalogueItemId;
import fr.vriege.anilib.feature.source.SourceContentUnit;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public record DownloadJobSnapshot(
        DownloadId id,
        LibraryItemId libraryItemId,
        String title,
        SourceCatalogueItemId sourceItemId,
        SourceContentUnit contentUnit,
        DownloadStatus status,
        DownloadPriority priority,
        int queuePosition,
        int completedPages,
        int totalPages,
        long downloadedBytes,
        Optional<String> error,
        long bytesPerSecond,
        Optional<Long> estimatedRemainingMillis,
        Instant updatedAt) {
    public DownloadJobSnapshot {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(libraryItemId, "libraryItemId must not be null");
        Objects.requireNonNull(title, "title must not be null");
        Objects.requireNonNull(sourceItemId, "sourceItemId must not be null");
        Objects.requireNonNull(contentUnit, "contentUnit must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(priority, "priority must not be null");
        if (queuePosition < 0) {
            throw new IllegalArgumentException("queuePosition must not be negative");
        }
        if (totalPages < 1 || completedPages < 0 || completedPages > totalPages) {
            throw new IllegalArgumentException("page progress must address the content unit");
        }
        if (downloadedBytes < 0) {
            throw new IllegalArgumentException("downloadedBytes must not be negative");
        }
        error = error.map(String::strip).filter(message -> !message.isEmpty());
        if (bytesPerSecond < 0) {
            throw new IllegalArgumentException("bytesPerSecond must not be negative");
        }
        estimatedRemainingMillis = Objects.requireNonNull(
                estimatedRemainingMillis,
                "estimatedRemainingMillis must not be null");
        if (estimatedRemainingMillis.stream().anyMatch(value -> value < 0)) {
            throw new IllegalArgumentException("estimatedRemainingMillis must not be negative");
        }
        Objects.requireNonNull(updatedAt, "updatedAt must not be null");
    }

    public double progress() {
        return (double) completedPages / totalPages;
    }

    public boolean hasPartialData() {
        return downloadedBytes > 0 && status != DownloadStatus.COMPLETED;
    }

    public Optional<Integer> failedPageIndex() {
        return status == DownloadStatus.FAILED && completedPages < totalPages
                ? Optional.of(completedPages)
                : Optional.empty();
    }
}
