package fr.vriege.anilib.feature.reader;

/** Reports unavailable content, invalid sessions, or page-pipeline failures. */
public final class ReaderException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public ReaderException(String message) {
        super(message);
    }

    public ReaderException(String message, Throwable cause) {
        super(message, cause);
    }
}
