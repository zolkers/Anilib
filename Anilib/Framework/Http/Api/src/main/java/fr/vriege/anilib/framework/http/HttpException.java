package fr.vriege.anilib.framework.http;

public final class HttpException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public HttpException(String message) {
        super(message);
    }

    public HttpException(String message, Throwable cause) {
        super(message, cause);
    }
}
