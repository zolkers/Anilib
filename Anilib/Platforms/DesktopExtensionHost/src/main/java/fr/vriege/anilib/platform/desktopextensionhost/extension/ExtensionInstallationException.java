package fr.vriege.anilib.platform.desktopextensionhost.extension;

public final class ExtensionInstallationException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public ExtensionInstallationException(String message, Throwable cause) {
        super(message, cause);
    }
}
