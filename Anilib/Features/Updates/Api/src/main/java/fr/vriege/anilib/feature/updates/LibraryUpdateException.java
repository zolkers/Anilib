package fr.vriege.anilib.feature.updates;

public final class LibraryUpdateException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public LibraryUpdateException(String message) {
        super(message);
    }

    public LibraryUpdateException(String message, Throwable cause) {
        super(message, cause);
    }
}
