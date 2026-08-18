package fr.vriege.anilib.feature.tracker;

import java.time.Instant;
import java.util.Objects;

public record TrackerSyncConflict(
        TrackerEntry localEntry,
        TrackerEntry remoteEntry,
        Instant detectedAt) {
    public TrackerSyncConflict {
        Objects.requireNonNull(localEntry, "localEntry must not be null");
        Objects.requireNonNull(remoteEntry, "remoteEntry must not be null");
        Objects.requireNonNull(detectedAt, "detectedAt must not be null");
        if (!localEntry.libraryItemId().equals(remoteEntry.libraryItemId())
                || !localEntry.trackerId().equals(remoteEntry.trackerId())
                || !localEntry.remoteId().equals(remoteEntry.remoteId())) {
            throw new IllegalArgumentException("conflict entries must describe the same remote binding");
        }
    }
}
