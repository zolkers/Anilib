package fr.vriege.anilib.feature.source;

/** Signals an incompatible or duplicate source registration. */
public final class SourceRegistrationException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public SourceRegistrationException(String message) {
        super(message);
    }
}
