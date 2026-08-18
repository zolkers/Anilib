package fr.vriege.anilib.framework.backup;

/** Signals malformed, unsupported, or uncommittable feature-owned backup data. */
public final class BackupCodecException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public BackupCodecException(String message) {
        super(message);
    }

    public BackupCodecException(String message, Throwable cause) {
        super(message, cause);
    }
}
