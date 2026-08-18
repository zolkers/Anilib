package fr.vriege.anilib.feature.tracker;

public final class TrackerException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public TrackerException(String message) {
        super(message);
    }

    public TrackerException(String message, Throwable cause) {
        super(message, cause);
    }
}
