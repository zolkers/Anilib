package fr.vriege.anilib.platform.desktopextensionhost.compat.android.webkit;

import java.net.URI;

public final class URLUtil {
    private URLUtil() {
    }

    public static boolean isValidUrl(String value) {
        try {
            URI uri = URI.create(value);
            return uri.getScheme() != null && uri.getHost() != null;
        } catch (IllegalArgumentException error) {
            return false;
        }
    }

    public static boolean isHttpUrl(String value) {
        return value != null && value.startsWith("http://");
    }

    public static boolean isHttpsUrl(String value) {
        return value != null && value.startsWith("https://");
    }
}
