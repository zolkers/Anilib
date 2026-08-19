package fr.vriege.anilib.kernel;

/**
 * Signals that a plugin graph could not be started, changed, or cleanly
 * released.
 *
 * <p>Graph validation failures use the exception message to identify the
 * violated invariant. Installation failures retain the plugin failure as their
 * cause. If rollback or shutdown cleanup also fails, those cleanup failures are
 * attached as {@linkplain Throwable#getSuppressed() suppressed exceptions} so
 * that the primary failure is preserved.</p>
 */
public final class PluginStartupException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    /**
     * Creates an exception with the supplied detail message.
     *
     * @param message the detail message; may be {@code null}
     */
    public PluginStartupException(String message) {
        super(message);
    }

    /**
     * Creates an exception with the supplied detail message and cause.
     *
     * @param message the detail message; may be {@code null}
     * @param cause   the underlying cause; may be {@code null}
     */
    public PluginStartupException(String message, Throwable cause) {
        super(message, cause);
    }
}
