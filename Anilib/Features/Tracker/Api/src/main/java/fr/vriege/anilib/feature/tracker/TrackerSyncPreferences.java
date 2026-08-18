package fr.vriege.anilib.feature.tracker;

import java.util.Objects;

public record TrackerSyncPreferences(
        boolean automatic,
        TrackerSyncDirection direction,
        TrackerConflictPolicy conflictPolicy) {
    public TrackerSyncPreferences {
        Objects.requireNonNull(direction, "direction must not be null");
        Objects.requireNonNull(conflictPolicy, "conflictPolicy must not be null");
    }

    public static TrackerSyncPreferences defaults() {
        return new TrackerSyncPreferences(false, TrackerSyncDirection.BIDIRECTIONAL, TrackerConflictPolicy.ASK);
    }

    public TrackerSyncPreferences withAutomatic(boolean value) {
        return new TrackerSyncPreferences(value, direction, conflictPolicy);
    }

    public TrackerSyncPreferences withDirection(TrackerSyncDirection value) {
        return new TrackerSyncPreferences(automatic, value, conflictPolicy);
    }

    public TrackerSyncPreferences withConflictPolicy(TrackerConflictPolicy value) {
        return new TrackerSyncPreferences(automatic, direction, value);
    }
}
