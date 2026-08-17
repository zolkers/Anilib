package fr.vriege.anilib.feature.downloads;

import fr.vriege.anilib.feature.library.LibraryItemId;

/** Durable queue commands and observable state shared by Android and desktop. */
public interface DownloadService {
    DownloadQueueSnapshot snapshot();

    boolean canEnqueue(LibraryItemId libraryItemId);

    DownloadId enqueue(LibraryItemId libraryItemId);

    void pause(DownloadId id);

    void resume(DownloadId id);

    void cancel(DownloadId id);

    void remove(DownloadId id);

    void pauseAll();

    void resumeAll();

    void setOfflineMode(boolean enabled);

    AutoCloseable observe(Runnable listener);
}
