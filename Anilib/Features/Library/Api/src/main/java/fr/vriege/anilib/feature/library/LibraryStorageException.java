package fr.vriege.anilib.feature.library;

/** Reports a durable catalog read, migration, or write failure. */
public final class LibraryStorageException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public LibraryStorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
