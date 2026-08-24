package fr.vriege.anilib.feature.downloads.runtime;

import fr.vriege.anilib.feature.downloads.DownloadId;
import fr.vriege.anilib.feature.downloads.DownloadContentType;
import fr.vriege.anilib.feature.downloads.DownloadJobSnapshot;
import fr.vriege.anilib.feature.downloads.DownloadPriority;
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
    final VideoDownloadMetadata video;
    DownloadPriority priority;
    long queueOrder;
    DownloadStatus status;
    int completedPages;
    long downloadedBytes;
    String error;
    Instant updatedAt;
    long bytesPerSecond;
    long activeStartedNanos;
    long activeStartBytes;
    long lastProgressNotificationNanos;

    DownloadRecord(
            DownloadId id,
            LibraryItemId libraryItemId,
            String title,
            SourceCatalogueItemId sourceItemId,
            SourceContentUnit contentUnit,
            List<SourcePageResource> pages,
            VideoDownloadMetadata video,
            DownloadPriority priority,
            long queueOrder,
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
        this.video = video;
        this.priority = priority;
        this.queueOrder = queueOrder;
        this.status = status;
        this.completedPages = completedPages;
        this.downloadedBytes = downloadedBytes;
        this.error = error;
        this.updatedAt = updatedAt;
    }

    boolean video() {
        return video != null;
    }

    DownloadJobSnapshot snapshot(int queuePosition) {
        return new DownloadJobSnapshot(
                id,
                libraryItemId,
                title,
                sourceItemId,
                contentUnit,
                video() ? DownloadContentType.VIDEO : DownloadContentType.PAGES,
                status,
                priority,
                queuePosition,
                completedPages,
                pages.size(),
                downloadedBytes,
                Optional.ofNullable(error),
                status == DownloadStatus.DOWNLOADING ? bytesPerSecond : 0L,
                estimatedRemainingMillis(),
                updatedAt);
    }

    private Optional<Long> estimatedRemainingMillis() {
        if (status != DownloadStatus.DOWNLOADING || bytesPerSecond < 1 || completedPages >= pages.size()) {
            return Optional.empty();
        }
        long knownBytes = 0L;
        int unknownPages = 0;
        for (int index = completedPages; index < pages.size(); index++) {
            long estimate = pages.get(index).estimatedBytes();
            if (estimate < 0) {
                unknownPages++;
            } else {
                knownBytes = saturatedAdd(knownBytes, estimate);
            }
        }
        long averagePageBytes = completedPages == 0 ? 0L : downloadedBytes / completedPages;
        long remainingBytes = saturatedAdd(knownBytes, saturatedMultiply(averagePageBytes, unknownPages));
        return remainingBytes == 0L
                ? Optional.empty()
                : Optional.of(Math.max(1L, saturatedMultiply(remainingBytes, 1000L) / bytesPerSecond));
    }

    private static long saturatedAdd(long left, long right) {
        if (right > 0 && left > Long.MAX_VALUE - right) {
            return Long.MAX_VALUE;
        }
        return left + right;
    }

    private static long saturatedMultiply(long left, long right) {
        if (left == 0L || right == 0L) {
            return 0L;
        }
        return left > Long.MAX_VALUE / right ? Long.MAX_VALUE : left * right;
    }
}
