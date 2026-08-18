package fr.vriege.anilib.feature.downloads;

import java.util.List;

public record AutomaticDownloadResult(
        int enqueuedJobs,
        int removedJobs,
        List<String> failures) {
    public AutomaticDownloadResult {
        if (enqueuedJobs < 0 || removedJobs < 0) {
            throw new IllegalArgumentException("automatic download counts must not be negative");
        }
        failures = List.copyOf(failures);
    }
}
