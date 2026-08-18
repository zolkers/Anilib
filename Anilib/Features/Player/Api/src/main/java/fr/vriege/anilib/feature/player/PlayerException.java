package fr.vriege.anilib.feature.player;

public final class PlayerException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public PlayerException(String message) {
        super(message);
    }

    public PlayerException(String message, Throwable cause) {
        super(message, cause);
    }
}
