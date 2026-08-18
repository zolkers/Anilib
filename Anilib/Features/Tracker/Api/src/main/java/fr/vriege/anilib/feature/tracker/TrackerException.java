package fr.vriege.anilib.feature.tracker;

/** Stable unchecked failure surfaced by tracker adapters and orchestration. */
public final class TrackerException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public TrackerException(String message) {
        super(message);
    }

    public TrackerException(String message, Throwable cause) {
        super(message, cause);
    }
}
