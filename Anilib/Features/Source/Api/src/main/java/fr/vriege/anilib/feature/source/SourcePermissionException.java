package fr.vriege.anilib.feature.source;

/** Raised when source code attempts to use a capability outside its declared grant. */
public final class SourcePermissionException extends IllegalStateException {
    private static final long serialVersionUID = 1L;

    public SourcePermissionException(String message) {
        super(message);
    }
}
