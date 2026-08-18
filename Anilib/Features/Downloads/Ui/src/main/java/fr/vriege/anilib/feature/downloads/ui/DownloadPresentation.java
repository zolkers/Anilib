package fr.vriege.anilib.feature.downloads.ui;

import fr.vriege.anilib.feature.downloads.DownloadId;
import fr.vriege.anilib.feature.downloads.DownloadQueueSnapshot;
import fr.vriege.anilib.feature.downloads.DownloadPriority;
import fr.vriege.anilib.feature.downloads.DownloadRecoveryMode;
import fr.vriege.anilib.feature.downloads.DownloadIndexRepairResult;
import fr.vriege.anilib.feature.downloads.DownloadStorageSnapshot;
import fr.vriege.anilib.feature.downloads.AutomaticDownloadPolicy;
import fr.vriege.anilib.feature.downloads.AutomaticDownloadResult;
import fr.vriege.anilib.feature.library.LibraryItemId;
import fr.vriege.anilib.feature.source.SourceContentUnitId;

import java.nio.file.Path;

public interface DownloadPresentation {
    DownloadQueueSnapshot queue();

    boolean canEnqueue(LibraryItemId libraryItemId);

    DownloadId enqueue(LibraryItemId libraryItemId);

    DownloadId enqueue(LibraryItemId libraryItemId, SourceContentUnitId contentUnitId);

    DownloadId enqueue(LibraryItemId libraryItemId, String sourceContentId);

    void pause(DownloadId id);

    void resume(DownloadId id);

    void cancel(DownloadId id);

    void remove(DownloadId id);

    void removeAll();

    void setPriority(DownloadId id, DownloadPriority priority);

    void move(DownloadId id, int queuePosition);

    void retry(DownloadId id, DownloadRecoveryMode mode);

    DownloadStorageSnapshot storage();

    void changeStorageLocation(Path location);

    DownloadIndexRepairResult repairIndex();

    AutomaticDownloadPolicy automaticPolicy();

    void configureAutomaticDownloads(AutomaticDownloadPolicy policy);

    AutomaticDownloadResult synchronizeAutomaticDownloads();

    int cleanAutomaticDownloads();

    void pauseTitle(LibraryItemId libraryItemId);

    void resumeTitle(LibraryItemId libraryItemId);

    void removeTitle(LibraryItemId libraryItemId);

    void pauseAll();

    void resumeAll();

    void setOfflineMode(boolean enabled);

    AutoCloseable observe(Runnable listener);
}
