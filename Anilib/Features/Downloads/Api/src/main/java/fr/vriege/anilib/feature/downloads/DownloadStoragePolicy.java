package fr.vriege.anilib.feature.downloads;

public record DownloadStoragePolicy(
        long maximumStorageBytes,
        long maximumPageBytes,
        int concurrentJobs,
        boolean resumeOnStart,
        boolean removePartialOnCancel) {
    public static final int MIN_CONCURRENT_JOBS = 1;
    public static final int MAX_CONCURRENT_JOBS = 8;

    public DownloadStoragePolicy {
        if (maximumStorageBytes < 1) {
            throw new IllegalArgumentException("maximumStorageBytes must be positive");
        }
        if (maximumPageBytes < 1 || maximumPageBytes > maximumStorageBytes) {
            throw new IllegalArgumentException(
                    "maximumPageBytes must be positive and not exceed maximumStorageBytes");
        }
        if (concurrentJobs < MIN_CONCURRENT_JOBS || concurrentJobs > MAX_CONCURRENT_JOBS) {
            throw new IllegalArgumentException("concurrentJobs must be between 1 and 8");
        }
    }

    public static DownloadStoragePolicy standard() {
        return new DownloadStoragePolicy(
                20L * 1024L * 1024L * 1024L,
                64L * 1024L * 1024L,
                2,
                true,
                true);
    }

    public DownloadStoragePolicy withMaximumStorageBytes(long maximumBytes) {
        return new DownloadStoragePolicy(
                maximumBytes,
                maximumPageBytes,
                concurrentJobs,
                resumeOnStart,
                removePartialOnCancel);
    }

    public DownloadStoragePolicy withConcurrentJobs(int jobs) {
        return new DownloadStoragePolicy(
                maximumStorageBytes,
                maximumPageBytes,
                jobs,
                resumeOnStart,
                removePartialOnCancel);
    }
}
