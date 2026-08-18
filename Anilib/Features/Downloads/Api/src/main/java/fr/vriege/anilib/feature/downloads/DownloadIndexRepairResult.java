package fr.vriege.anilib.feature.downloads;

public record DownloadIndexRepairResult(
        int repairedJobs,
        int orphanedDirectoriesRemoved,
        long indexedBytes) {
    public DownloadIndexRepairResult {
        if (repairedJobs < 0 || orphanedDirectoriesRemoved < 0 || indexedBytes < 0L) {
            throw new IllegalArgumentException("repair values must not be negative");
        }
    }
}
