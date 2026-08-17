package fr.vriege.anilib.feature.downloads;

/** Durable queue states visible in the shared Download queue screen. */
public enum DownloadStatus {
    QUEUED,
    DOWNLOADING,
    PAUSED,
    COMPLETED,
    FAILED,
    CANCELLED
}
