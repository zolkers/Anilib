package fr.vriege.anilib.kernel;

public final class PluginStartupException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public PluginStartupException(String message) {
        super(message);
    }

    public PluginStartupException(String message, Throwable cause) {
        super(message, cause);
    }
}
