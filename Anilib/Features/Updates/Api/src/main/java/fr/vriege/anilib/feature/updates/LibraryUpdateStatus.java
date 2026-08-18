package fr.vriege.anilib.feature.updates;

/** Current state of the single library update job. */
public enum LibraryUpdateStatus {
    IDLE,
    RUNNING,
    COMPLETED,
    FAILED,
    CANCELLED
}
