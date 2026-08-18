package fr.vriege.anilib.feature.reader;

public record ReaderPolicy(int prefetchDistance, long maximumCacheBytes, long maximumPageBytes) {
    public ReaderPolicy {
        if (prefetchDistance < 0 || prefetchDistance > 10) {
            throw new IllegalArgumentException("prefetchDistance must be between 0 and 10");
        }
        if (maximumCacheBytes < 1) {
            throw new IllegalArgumentException("maximumCacheBytes must be positive");
        }
        if (maximumPageBytes < 1 || maximumPageBytes > maximumCacheBytes) {
            throw new IllegalArgumentException(
                    "maximumPageBytes must be positive and not exceed maximumCacheBytes");
        }
    }

    public static ReaderPolicy standard() {
        return new ReaderPolicy(3, 64L * 1024L * 1024L, 32L * 1024L * 1024L);
    }
}
