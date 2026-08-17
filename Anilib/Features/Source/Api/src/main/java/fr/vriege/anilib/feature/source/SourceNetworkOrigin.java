package fr.vriege.anilib.feature.source;

import fr.vriege.anilib.foundation.validation.Preconditions;

import java.net.URI;
import java.util.Locale;

/** Exact HTTP origin granted to one source Bundle. Wildcards are deliberately unsupported. */
public record SourceNetworkOrigin(String scheme, String host, int port) implements Comparable<SourceNetworkOrigin> {
    private static final int HTTP_PORT = 80;
    private static final int HTTPS_PORT = 443;

    public SourceNetworkOrigin {
        scheme = Preconditions.requireNonBlank(scheme, "scheme").toLowerCase(Locale.ROOT);
        if (!scheme.equals("http") && !scheme.equals("https")) {
            throw new IllegalArgumentException("scheme must be http or https");
        }
        host = normalizedHost(host);
        if (port < 1 || port > 65_535) {
            throw new IllegalArgumentException("port must be between 1 and 65535");
        }
    }

    public static SourceNetworkOrigin https(String host) {
        return new SourceNetworkOrigin("https", host, HTTPS_PORT);
    }

    public static SourceNetworkOrigin http(String host) {
        return new SourceNetworkOrigin("http", host, HTTP_PORT);
    }

    public boolean matches(URI uri) {
        URI value = Preconditions.requireNonNull(uri, "uri");
        String requestHost = value.getHost();
        if (requestHost == null) {
            return false;
        }
        int requestPort = value.getPort() < 0 ? defaultPort(value.getScheme()) : value.getPort();
        return scheme.equalsIgnoreCase(value.getScheme())
                && host.equalsIgnoreCase(requestHost)
                && port == requestPort;
    }

    @Override
    public int compareTo(SourceNetworkOrigin other) {
        return toString().compareTo(other.toString());
    }

    @Override
    public String toString() {
        int defaultPort = defaultPort(scheme);
        return scheme + "://" + host + (port == defaultPort ? "" : ":" + port);
    }

    private static String normalizedHost(String host) {
        String value = Preconditions.requireNonBlank(host, "host").toLowerCase(Locale.ROOT);
        URI uri = URI.create("https://" + value);
        if (uri.getHost() == null || uri.getUserInfo() != null || uri.getPort() >= 0
                || !uri.getPath().isEmpty() || uri.getQuery() != null || uri.getFragment() != null) {
            throw new IllegalArgumentException("host must be one exact DNS name or IP address");
        }
        return uri.getHost().toLowerCase(Locale.ROOT);
    }

    private static int defaultPort(String scheme) {
        if (scheme != null && scheme.equalsIgnoreCase("http")) {
            return HTTP_PORT;
        }
        if (scheme != null && scheme.equalsIgnoreCase("https")) {
            return HTTPS_PORT;
        }
        return -1;
    }
}
