package fr.vriege.anilib.feature.downloads.ui;

import fr.vriege.anilib.feature.downloads.DownloadId;
import fr.vriege.anilib.feature.downloads.DownloadQueueSnapshot;
import fr.vriege.anilib.feature.library.LibraryItemId;

/** Platform-neutral Download queue presentation and commands. */
public interface DownloadPresentation {
    DownloadQueueSnapshot queue();

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
