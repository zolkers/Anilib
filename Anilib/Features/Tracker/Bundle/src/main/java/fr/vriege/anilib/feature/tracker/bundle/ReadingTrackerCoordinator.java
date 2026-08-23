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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class ReadingTrackerCoordinator implements AutoCloseable {
    private static final System.Logger LOGGER = System.getLogger(ReadingTrackerCoordinator.class.getName());
    private static final Pattern LABELED_CHAPTER_NUMBER = Pattern.compile(
            "(?iu)\\b(?:chapter|chapitre|chap|ch|capitulo|capítulo|cap)\\.?\\s*[:#-]?\\s*"
                    + "(\\d+(?:[.,]\\d+)?)");
    private static final Pattern BARE_CHAPTER_NUMBER = Pattern.compile(
            "^\\s*#?\\s*(\\d+(?:[.,]\\d+)?)(?:\\s|$)");
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
            synchronizer.execute(() -> {
                try {
                    advance(event);
                } catch (RuntimeException failure) {
                    LOGGER.log(
                            System.Logger.Level.ERROR,
                            "Unable to synchronize reading progress for " + event.libraryItemId(),
                            failure);
                }
            });
        } catch (RejectedExecutionException ignored) {
            // Closing the product may race with a final reading persistence callback.
        }
    }

    private void advance(ReaderReadEvent event) {
        Set<String> readIds = readState.readContentIds(event.libraryItemId());
        double highestRead = reader.contentUnits(event.libraryItemId()).stream()
                .filter(unit -> readIds.contains(unit.id().value()))
                .mapToDouble(ReadingTrackerCoordinator::progress)
                .filter(number -> number >= 0.0d)
                .max()
                .orElse(SourceContentUnit.UNKNOWN_NUMBER);
        List<TrackerEntry> entries = tracker.entries(event.libraryItemId());
        double highestTracked = entries.stream().mapToDouble(TrackerEntry::progress).max().orElse(-1.0d);
        if (highestRead > highestTracked) {
            tracker.synchronizeProgress(event.libraryItemId(), highestRead, -1L);
        }
    }

    private static double progress(SourceContentUnit unit) {
        if (unit.number() >= 0.0d) {
            return unit.number();
        }
        Matcher labeled = LABELED_CHAPTER_NUMBER.matcher(unit.title());
        if (labeled.find()) {
            return decimal(labeled.group(1));
        }
        Matcher bare = BARE_CHAPTER_NUMBER.matcher(unit.title());
        return bare.find() ? decimal(bare.group(1)) : SourceContentUnit.UNKNOWN_NUMBER;
    }

    private static double decimal(String value) {
        try {
            double parsed = Double.parseDouble(value.replace(',', '.'));
            return Double.isFinite(parsed) ? parsed : SourceContentUnit.UNKNOWN_NUMBER;
        } catch (NumberFormatException ignored) {
            return SourceContentUnit.UNKNOWN_NUMBER;
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
