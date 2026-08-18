package fr.vriege.anilib.feature.updates;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Immutable state consumed by the Updates tab and native notification adapters. */
public record LibraryUpdateSnapshot(
        LibraryUpdatePolicy policy,
        LibraryUpdateStatus status,
        int completedTitles,
        int totalTitles,
        List<String> activeTitles,
        List<LibraryUpdateEvent> events,
        List<LibraryUpdateFailure> failures,
        Optional<Instant> lastRunAt,
        Optional<Instant> nextRunAt) {
    public LibraryUpdateSnapshot {
        Objects.requireNonNull(policy, "policy must not be null");
        Objects.requireNonNull(status, "status must not be null");
        if (completedTitles < 0 || totalTitles < 0 || completedTitles > totalTitles) {
            throw new IllegalArgumentException("update progress is invalid");
        }
        activeTitles = List.copyOf(activeTitles);
        events = List.copyOf(events);
        failures = List.copyOf(failures);
        lastRunAt = Objects.requireNonNull(lastRunAt, "lastRunAt must not be null");
        nextRunAt = Objects.requireNonNull(nextRunAt, "nextRunAt must not be null");
    }

    public long unreadCount() {
        return events.stream().filter(event -> !event.read()).count();
    }
}
