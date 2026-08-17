package fr.vriege.anilib.feature.downloads.ui;

import fr.vriege.anilib.feature.downloads.DownloadId;
import fr.vriege.anilib.feature.downloads.DownloadQueueSnapshot;
import fr.vriege.anilib.feature.downloads.DownloadService;
import fr.vriege.anilib.feature.library.LibraryItemId;

import java.util.Objects;

/** Default shared presentation adapter over the durable Download service. */
public final class DefaultDownloadPresentation implements DownloadPresentation {
    private final DownloadService downloads;

    public DefaultDownloadPresentation(DownloadService downloads) {
        this.downloads = Objects.requireNonNull(downloads, "downloads must not be null");
    }

    @Override
    public DownloadQueueSnapshot queue() {
        return downloads.snapshot();
    }

    @Override
    public boolean canEnqueue(LibraryItemId libraryItemId) {
        return downloads.canEnqueue(libraryItemId);
    }

    @Override
    public DownloadId enqueue(LibraryItemId libraryItemId) {
        return downloads.enqueue(libraryItemId);
    }

    @Override
    public void pause(DownloadId id) {
        downloads.pause(id);
    }

    @Override
    public void resume(DownloadId id) {
        downloads.resume(id);
    }

    @Override
    public void cancel(DownloadId id) {
        downloads.cancel(id);
    }

    @Override
    public void remove(DownloadId id) {
        downloads.remove(id);
    }

    @Override
    public void pauseAll() {
        downloads.pauseAll();
    }

    @Override
    public void resumeAll() {
        downloads.resumeAll();
    }

    @Override
    public void setOfflineMode(boolean enabled) {
        downloads.setOfflineMode(enabled);
    }

    @Override
    public AutoCloseable observe(Runnable listener) {
        return downloads.observe(listener);
    }
}
