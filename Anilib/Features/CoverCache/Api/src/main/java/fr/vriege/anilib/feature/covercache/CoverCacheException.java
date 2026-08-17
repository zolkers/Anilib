package fr.vriege.anilib.feature.covercache;

/** Reports loading, decoding, or durable cache failures. */
public final class CoverCacheException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public CoverCacheException(String message) {
        super(message);
    }

    public CoverCacheException(String message, Throwable cause) {
        super(message, cause);
    }
}
