package fr.vriege.anilib.platform.desktopextensionhost.compat.android.util;

public final class Log {
    private static final System.Logger LOGGER = System.getLogger(Log.class.getName());

    private Log() {
    }

    public static int d(String tag, String message) {
        return write(System.Logger.Level.DEBUG, tag, message, null);
    }

    public static int i(String tag, String message) {
        return write(System.Logger.Level.INFO, tag, message, null);
    }

    public static int w(String tag, String message) {
        return write(System.Logger.Level.WARNING, tag, message, null);
    }

    public static int w(String tag, String message, Throwable error) {
        return write(System.Logger.Level.WARNING, tag, message, error);
    }

    public static int e(String tag, String message) {
        return write(System.Logger.Level.ERROR, tag, message, null);
    }

    public static int e(String tag, String message, Throwable error) {
        return write(System.Logger.Level.ERROR, tag, message, error);
    }

    public static int wtf(String tag, String message) {
        return write(System.Logger.Level.ERROR, tag, message, null);
    }

    public static int wtf(String tag, String message, Throwable error) {
        return write(System.Logger.Level.ERROR, tag, message, error);
    }

    private static int write(System.Logger.Level level, String tag, String message, Throwable error) {
        String formatted = '[' + String.valueOf(tag) + "] " + String.valueOf(message);
        if (error == null) {
            LOGGER.log(level, formatted);
        } else {
            LOGGER.log(level, formatted, error);
        }
        return 0;
    }
}
