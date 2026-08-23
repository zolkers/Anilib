package fr.vriege.anilib.feature.tracker;

import java.net.URI;
import java.util.Objects;

public record TrackerAuthorization(URI authorizationUri, URI callbackUri) {
    public TrackerAuthorization {
        authorizationUri = requireAbsolute(authorizationUri, "authorizationUri");
        if (!"https".equalsIgnoreCase(authorizationUri.getScheme())) {
            throw new IllegalArgumentException("authorizationUri must use HTTPS");
        }
        callbackUri = requireAbsolute(callbackUri, "callbackUri");
    }

    public boolean accepts(URI candidate) {
        URI value = Objects.requireNonNull(candidate, "candidate must not be null");
        return callbackUri.getScheme().equalsIgnoreCase(value.getScheme())
                && equalHost(callbackUri.getHost(), value.getHost())
                && callbackUri.getPort() == value.getPort()
                && Objects.equals(callbackUri.getPath(), value.getPath());
    }

    private static boolean equalHost(String expected, String actual) {
        return expected == null ? actual == null : expected.equalsIgnoreCase(actual);
    }

    private static URI requireAbsolute(URI value, String name) {
        URI uri = Objects.requireNonNull(value, name + " must not be null");
        if (!uri.isAbsolute() || uri.getScheme() == null) {
            throw new IllegalArgumentException(name + " must be absolute");
        }
        return uri;
    }
}
