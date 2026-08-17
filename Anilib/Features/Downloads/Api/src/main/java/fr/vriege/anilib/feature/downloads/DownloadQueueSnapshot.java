package fr.vriege.anilib.feature.downloads;

import java.util.List;

/** Immutable queue and storage summary consumed by every platform UI. */
public record DownloadQueueSnapshot(
        List<DownloadJobSnapshot> jobs,
        boolean offlineMode,
        long usedStorageBytes,
        long maximumStorageBytes) {
    public DownloadQueueSnapshot {
        jobs = List.copyOf(jobs);
        if (usedStorageBytes < 0 || maximumStorageBytes < 1) {
            throw new IllegalArgumentException("storage values must be non-negative with a positive maximum");
        }
    }
}
