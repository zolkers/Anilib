package fr.vriege.anilib.feature.tracker;

import java.net.URI;
import java.util.Locale;
import java.util.Objects;

/** Exact scheme, host, and optional port granted to a network tracker. */
public record TrackerNetworkOrigin(String scheme, String host, int port) {
    public static final int DEFAULT_PORT = -1;

    public TrackerNetworkOrigin {
        scheme = normalize(scheme, "scheme");
        host = normalize(host, "host");
        if (!(scheme.equals("http") || scheme.equals("https"))) {
            throw new IllegalArgumentException("tracker origin scheme must be HTTP or HTTPS");
        }
        if (port < DEFAULT_PORT || port == 0 || port > 65_535) {
            throw new IllegalArgumentException("tracker origin port is invalid");
        }
    }

    public static TrackerNetworkOrigin of(String scheme, String host) {
        return new TrackerNetworkOrigin(scheme, host, DEFAULT_PORT);
    }

    public boolean matches(URI uri) {
        Objects.requireNonNull(uri, "uri must not be null");
        int effectivePort = uri.getPort();
        return scheme.equalsIgnoreCase(uri.getScheme())
                && host.equalsIgnoreCase(uri.getHost())
                && port == effectivePort;
    }

    private static String normalize(String value, String name) {
        String normalized = Objects.requireNonNull(value, name + " must not be null")
                .strip().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return normalized;
    }
}
