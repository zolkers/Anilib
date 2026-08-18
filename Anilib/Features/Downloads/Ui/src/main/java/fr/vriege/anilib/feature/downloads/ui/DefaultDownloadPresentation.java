package fr.vriege.anilib.feature.downloads.ui;

import fr.vriege.anilib.feature.downloads.DownloadId;
import fr.vriege.anilib.feature.downloads.DownloadQueueSnapshot;
import fr.vriege.anilib.feature.downloads.DownloadPriority;
import fr.vriege.anilib.feature.downloads.DownloadRecoveryMode;
import fr.vriege.anilib.feature.downloads.DownloadIndexRepairResult;
import fr.vriege.anilib.feature.downloads.DownloadStorageSnapshot;
import fr.vriege.anilib.feature.downloads.DownloadService;
import fr.vriege.anilib.feature.library.LibraryItemId;
import fr.vriege.anilib.feature.source.SourceContentUnitId;

import java.util.Objects;
import java.nio.file.Path;

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
    public DownloadId enqueue(LibraryItemId libraryItemId, SourceContentUnitId contentUnitId) {
        return downloads.enqueue(libraryItemId, contentUnitId);
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
    public void removeAll() {
        downloads.removeAll();
    }

    @Override
    public void setPriority(DownloadId id, DownloadPriority priority) {
        downloads.setPriority(id, priority);
    }

    @Override
    public void move(DownloadId id, int queuePosition) {
        downloads.move(id, queuePosition);
    }

    @Override
    public void retry(DownloadId id, DownloadRecoveryMode mode) {
        downloads.retry(id, mode);
    }

    @Override
    public DownloadStorageSnapshot storage() {
        return downloads.storage();
    }

    @Override
    public void changeStorageLocation(Path location) {
        downloads.changeStorageLocation(location);
    }

    @Override
    public DownloadIndexRepairResult repairIndex() {
        return downloads.repairIndex();
    }

    @Override
    public void pauseTitle(LibraryItemId libraryItemId) {
        downloads.pauseTitle(libraryItemId);
    }

    @Override
    public void resumeTitle(LibraryItemId libraryItemId) {
        downloads.resumeTitle(libraryItemId);
    }

    @Override
    public void removeTitle(LibraryItemId libraryItemId) {
        downloads.removeTitle(libraryItemId);
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
