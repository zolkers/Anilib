package fr.vriege.anilib.feature.tracker.bundle;

import fr.vriege.anilib.feature.player.PlayerProgressEvent;
import fr.vriege.anilib.feature.player.PlayerService;
import fr.vriege.anilib.feature.tracker.TrackerEntry;
import fr.vriege.anilib.feature.tracker.TrackerService;
import fr.vriege.anilib.framework.concurrent.runtime.ManagedExecutors;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;

final class PlaybackTrackerCoordinator implements AutoCloseable {
    private final TrackerService tracker;
    private final ExecutorService synchronizer = ManagedExecutors.single("anilib-playback-tracker");
    private final AutoCloseable observation;
    private volatile boolean closed;

    PlaybackTrackerCoordinator(PlayerService player, TrackerService tracker) {
        this.tracker = Objects.requireNonNull(tracker, "tracker must not be null");
        observation = Objects.requireNonNull(player, "player must not be null")
                .observeProgress(this::queue);
    }

    private void queue(PlayerProgressEvent event) {
        if (closed) {
            return;
        }
        try {
            synchronizer.execute(() -> advance(event));
        } catch (RejectedExecutionException ignored) {
            // Closing the product may race with a final playback persistence callback.
        }
    }

    private void advance(PlayerProgressEvent event) {
        List<TrackerEntry> entries = tracker.entries(event.libraryItemId());
        double highest = entries.stream().mapToDouble(TrackerEntry::progress).max().orElse(-1.0d);
        if (event.episodeNumber() > highest) {
            tracker.synchronizeProgress(event.libraryItemId(), event.episodeNumber(), -1L);
        }
    }

    @Override
    public void close() {
        closed = true;
        try {
            observation.close();
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to close playback tracking observation", exception);
        } finally {
            ManagedExecutors.shutdown(synchronizer);
        }
    }
}
