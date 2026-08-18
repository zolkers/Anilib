package fr.vriege.anilib.feature.downloads;

public record DownloadStoragePolicy(
        long maximumStorageBytes,
        long maximumPageBytes,
        int concurrentJobs,
        boolean resumeOnStart,
        boolean removePartialOnCancel) {
    public DownloadStoragePolicy {
        if (maximumStorageBytes < 1) {
            throw new IllegalArgumentException("maximumStorageBytes must be positive");
        }
        if (maximumPageBytes < 1 || maximumPageBytes > maximumStorageBytes) {
            throw new IllegalArgumentException(
                    "maximumPageBytes must be positive and not exceed maximumStorageBytes");
        }
        if (concurrentJobs < 1 || concurrentJobs > 8) {
            throw new IllegalArgumentException("concurrentJobs must be between 1 and 8");
        }
    }

    public static DownloadStoragePolicy standard() {
        return new DownloadStoragePolicy(
                4L * 1024L * 1024L * 1024L,
                64L * 1024L * 1024L,
                2,
                true,
                true);
    }
}
