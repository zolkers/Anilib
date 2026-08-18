package fr.vriege.anilib.framework.backup;

public final class BackupCodecException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public BackupCodecException(String message) {
        super(message);
    }

    public BackupCodecException(String message, Throwable cause) {
        super(message, cause);
    }
}
