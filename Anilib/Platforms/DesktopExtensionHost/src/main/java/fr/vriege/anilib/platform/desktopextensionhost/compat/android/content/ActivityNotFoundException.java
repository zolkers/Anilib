package fr.vriege.anilib.platform.desktopextensionhost.compat.android.content;

public class ActivityNotFoundException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public ActivityNotFoundException() {
    }

    public ActivityNotFoundException(String message) {
        super(message);
    }
}
