package fr.vriege.anilib.feature.backup;

/** Reports an invalid archive or a failed durable backup operation. */
public final class BackupException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public BackupException(String message) {
        super(message);
    }

    public BackupException(String message, Throwable cause) {
        super(message, cause);
    }
}
