package fr.vriege.anilib.feature.downloads;

import fr.vriege.anilib.feature.library.LibraryItemId;
import fr.vriege.anilib.feature.source.SourceContentUnitId;

public interface DownloadService {
    DownloadQueueSnapshot snapshot();

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
