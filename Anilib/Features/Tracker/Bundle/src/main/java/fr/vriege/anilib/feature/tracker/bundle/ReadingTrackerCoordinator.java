package fr.vriege.anilib.feature.tracker.bundle;

import fr.vriege.anilib.feature.reader.ReaderReadEvent;
import fr.vriege.anilib.feature.reader.ReaderReadStateStore;
import fr.vriege.anilib.feature.reader.ReaderService;
import fr.vriege.anilib.feature.source.SourceContentUnit;
import fr.vriege.anilib.feature.tracker.TrackerEntry;
import fr.vriege.anilib.feature.tracker.TrackerService;
import fr.vriege.anilib.framework.concurrent.runtime.ManagedExecutors;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;

final class ReadingTrackerCoordinator implements AutoCloseable {
    private final ReaderService reader;
    private final ReaderReadStateStore readState;
    private final TrackerService tracker;
    private final ExecutorService synchronizer = ManagedExecutors.single("anilib-reading-tracker");
    private final AutoCloseable observation;
    private volatile boolean closed;

    ReadingTrackerCoordinator(
            ReaderService reader,
            ReaderReadStateStore readState,
            TrackerService tracker) {
        this.reader = Objects.requireNonNull(reader, "reader must not be null");
        this.readState = Objects.requireNonNull(readState, "readState must not be null");
        this.tracker = Objects.requireNonNull(tracker, "tracker must not be null");
        observation = readState.observe(this::queue);
    }

    private void queue(ReaderReadEvent event) {
        if (closed || !event.read()) {
            return;
        }
        try {
            synchronizer.execute(() -> advance(event));
        } catch (RejectedExecutionException ignored) {
            // Closing the product may race with a final reading persistence callback.
        }
    }

    private void advance(ReaderReadEvent event) {
        Set<String> readIds = readState.readContentIds(event.libraryItemId());
        double highestRead = reader.contentUnits(event.libraryItemId()).stream()
                .filter(unit -> readIds.contains(unit.id().value()))
                .mapToDouble(SourceContentUnit::number)
                .filter(number -> number >= 0.0d)
                .max()
                .orElse(SourceContentUnit.UNKNOWN_NUMBER);
        List<TrackerEntry> entries = tracker.entries(event.libraryItemId());
        double highestTracked = entries.stream().mapToDouble(TrackerEntry::progress).max().orElse(-1.0d);
        if (highestRead > highestTracked) {
            tracker.synchronizeProgress(event.libraryItemId(), highestRead, -1L);
        }
    }

    @Override
    public void close() {
        closed = true;
        try {
            observation.close();
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to close reading tracking observation", exception);
        } finally {
            ManagedExecutors.shutdown(synchronizer);
        }
    }
}
