package fr.vriege.anilib.feature.downloads.ui;

import fr.vriege.anilib.feature.downloads.DownloadId;
import fr.vriege.anilib.feature.downloads.DownloadQueueSnapshot;
import fr.vriege.anilib.feature.library.LibraryItemId;
import fr.vriege.anilib.feature.source.SourceContentUnitId;

public interface DownloadPresentation {
    DownloadQueueSnapshot queue();

    boolean canEnqueue(LibraryItemId libraryItemId);

    DownloadId enqueue(LibraryItemId libraryItemId);

    DownloadId enqueue(LibraryItemId libraryItemId, SourceContentUnitId contentUnitId);

    void pause(DownloadId id);

    void resume(DownloadId id);

    void cancel(DownloadId id);

    void remove(DownloadId id);

    void pauseAll();

    void resumeAll();

    void setOfflineMode(boolean enabled);

    AutoCloseable observe(Runnable listener);
}
