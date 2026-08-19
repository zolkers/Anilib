package fr.vriege.anilib.framework.backup;

/**
 * Signals that a feature-owned backup section cannot be encoded, decoded,
 * inspected, or prepared for restoration.
 *
 * <p>Codecs should use this exception to preserve the distinction between an
 * invalid section payload and failures of the archive or storage layer. The
 * original parsing or persistence failure may be retained as the cause.</p>
 */
public final class BackupCodecException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    /**
     * Creates an exception with the supplied detail message.
     *
     * @param message the detail message; may be {@code null}
     */
    public BackupCodecException(String message) {
        super(message);
    }

    /**
     * Creates an exception with the supplied detail message and cause.
     *
     * @param message the detail message; may be {@code null}
     * @param cause   the underlying cause; may be {@code null}
     */
    public BackupCodecException(String message, Throwable cause) {
        super(message, cause);
    }
}
