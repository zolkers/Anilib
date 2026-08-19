package fr.vriege.anilib.framework.http;

/**
 * Signals failure of HTTP policy, transport, cookie, rate-limit, or cache
 * processing.
 *
 * <p>HTTP protocol status codes do not by themselves cause this exception;
 * they are represented by a normally returned {@link HttpResponse}.</p>
 */
public final class HttpException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    /**
     * Creates an exception with the supplied detail message.
     *
     * @param message the detail message; may be {@code null}
     */
    public HttpException(String message) {
        super(message);
    }

    /**
     * Creates an exception with the supplied detail message and cause.
     *
     * @param message the detail message; may be {@code null}
     * @param cause   the underlying cause; may be {@code null}
     */
    public HttpException(String message, Throwable cause) {
        super(message, cause);
    }
}
