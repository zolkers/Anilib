package fr.vriege.anilib.feature.backup;

public final class BackupException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public BackupException(String message) {
        super(message);
    }

    public BackupException(String message, Throwable cause) {
        super(message, cause);
    }
}
