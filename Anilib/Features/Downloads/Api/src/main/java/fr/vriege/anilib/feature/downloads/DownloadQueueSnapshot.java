package fr.vriege.anilib.feature.downloads;

import java.util.List;

public record DownloadQueueSnapshot(
        List<DownloadJobSnapshot> jobs,
        boolean offlineMode,
        long usedStorageBytes,
        long maximumStorageBytes,
        int concurrentJobs) {
    public DownloadQueueSnapshot {
        jobs = List.copyOf(jobs);
        if (usedStorageBytes < 0 || maximumStorageBytes < 1) {
            throw new IllegalArgumentException("storage values must be non-negative with a positive maximum");
        }
        if (concurrentJobs < DownloadStoragePolicy.MIN_CONCURRENT_JOBS
                || concurrentJobs > DownloadStoragePolicy.MAX_CONCURRENT_JOBS) {
            throw new IllegalArgumentException("concurrentJobs must be between 1 and 8");
        }
    }
}
