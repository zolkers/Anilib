package fr.vriege.anilib.feature.downloads.runtime;

import fr.vriege.anilib.feature.downloads.DownloadService;
import fr.vriege.anilib.feature.updates.LibraryUpdateService;
import fr.vriege.anilib.feature.updates.LibraryUpdateSnapshot;
import fr.vriege.anilib.feature.updates.LibraryUpdateStatus;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public final class AutomaticDownloadUpdateCoordinator implements AutoCloseable {
    private final DownloadService downloads;
    private final LibraryUpdateService updates;
    private final AutoCloseable registration;
    private Optional<Instant> processedRun;

    public AutomaticDownloadUpdateCoordinator(DownloadService downloads, LibraryUpdateService updates) {
        this.downloads = Objects.requireNonNull(downloads, "downloads must not be null");
        this.updates = Objects.requireNonNull(updates, "updates must not be null");
        this.processedRun = updates.snapshot().lastRunAt();
        this.registration = updates.observe(this::updatesChanged);
    }

    private synchronized void updatesChanged() {
        LibraryUpdateSnapshot snapshot = updates.snapshot();
        if (snapshot.status() != LibraryUpdateStatus.COMPLETED
                || snapshot.lastRunAt().isEmpty()
                || snapshot.lastRunAt().equals(processedRun)) {
            return;
        }
        processedRun = snapshot.lastRunAt();
        downloads.synchronizeAutomaticDownloads();
    }

    @Override
    public void close() {
        try {
            registration.close();
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to stop automatic download coordination", exception);
        }
    }
}
