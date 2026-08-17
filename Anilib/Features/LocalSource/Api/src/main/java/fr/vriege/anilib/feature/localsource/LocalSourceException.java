package fr.vriege.anilib.feature.localsource;

/** Reports local discovery, archive, security, or page-read failures. */
public final class LocalSourceException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public LocalSourceException(String message) {
        super(message);
    }

    public LocalSourceException(String message, Throwable cause) {
        super(message, cause);
    }
}
