package fr.vriege.anilib.feature.tracker;

import java.util.List;

public record TrackerSyncReport(
        int pushed,
        int pulled,
        List<TrackerSyncConflict> conflicts,
        List<String> failures) {
    public TrackerSyncReport {
        if (pushed < 0 || pulled < 0) {
            throw new IllegalArgumentException("synchronization counts must not be negative");
        }
        conflicts = List.copyOf(conflicts);
        failures = List.copyOf(failures);
        if (failures.stream().anyMatch(String::isBlank)) {
            throw new IllegalArgumentException("failures must not contain blank messages");
        }
    }

    public static TrackerSyncReport empty() {
        return new TrackerSyncReport(0, 0, List.of(), List.of());
    }
}
