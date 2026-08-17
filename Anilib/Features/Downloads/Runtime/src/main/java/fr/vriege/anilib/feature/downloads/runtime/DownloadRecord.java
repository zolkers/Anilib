package fr.vriege.anilib.feature.downloads.runtime;

import fr.vriege.anilib.feature.downloads.DownloadId;
import fr.vriege.anilib.feature.downloads.DownloadJobSnapshot;
import fr.vriege.anilib.feature.downloads.DownloadStatus;
import fr.vriege.anilib.feature.library.LibraryItemId;
import fr.vriege.anilib.feature.source.SourceCatalogueItemId;
import fr.vriege.anilib.feature.source.SourceContentUnit;
import fr.vriege.anilib.feature.source.SourcePageResource;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

final class DownloadRecord {
    final DownloadId id;
    final LibraryItemId libraryItemId;
    final String title;
    final SourceCatalogueItemId sourceItemId;
    final SourceContentUnit contentUnit;
    final List<SourcePageResource> pages;
    DownloadStatus status;
    int completedPages;
    long downloadedBytes;
    String error;
    Instant updatedAt;

    DownloadRecord(
            DownloadId id,
            LibraryItemId libraryItemId,
            String title,
            SourceCatalogueItemId sourceItemId,
            SourceContentUnit contentUnit,
            List<SourcePageResource> pages,
            DownloadStatus status,
            int completedPages,
            long downloadedBytes,
            String error,
            Instant updatedAt) {
        this.id = id;
        this.libraryItemId = libraryItemId;
        this.title = title;
        this.sourceItemId = sourceItemId;
        this.contentUnit = contentUnit;
        this.pages = List.copyOf(pages);
        this.status = status;
        this.completedPages = completedPages;
        this.downloadedBytes = downloadedBytes;
        this.error = error;
        this.updatedAt = updatedAt;
    }

    DownloadJobSnapshot snapshot() {
        return new DownloadJobSnapshot(
                id,
                libraryItemId,
                title,
                sourceItemId,
                contentUnit,
                status,
                completedPages,
                pages.size(),
                downloadedBytes,
                Optional.ofNullable(error),
                updatedAt);
    }
}
