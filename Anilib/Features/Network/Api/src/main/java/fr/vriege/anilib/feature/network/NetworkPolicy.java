package fr.vriege.anilib.feature.network;

import java.net.URI;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

public record NetworkPolicy(
        String userAgent,
        Optional<URI> proxy,
        Optional<URI> dnsOverHttps,
        Duration timeout,
        boolean responseCacheEnabled) {
    private static final int MAX_USER_AGENT_LENGTH = 256;

    public NetworkPolicy {
        userAgent = Objects.requireNonNull(userAgent, "userAgent must not be null").strip();
        if (userAgent.isEmpty() || userAgent.length() > MAX_USER_AGENT_LENGTH
                || userAgent.indexOf('\r') >= 0 || userAgent.indexOf('\n') >= 0) {
            throw new IllegalArgumentException("userAgent must contain 1 to 256 safe characters");
        }
        proxy = Objects.requireNonNull(proxy, "proxy must not be null").map(NetworkPolicy::proxyUri);
        dnsOverHttps = Objects.requireNonNull(dnsOverHttps, "dnsOverHttps must not be null")
                .map(NetworkPolicy::dohUri);
        timeout = Objects.requireNonNull(timeout, "timeout must not be null");
        if (timeout.compareTo(Duration.ofSeconds(1)) < 0
                || timeout.compareTo(Duration.ofMinutes(2)) > 0) {
            throw new IllegalArgumentException("timeout must be between 1 and 120 seconds");
        }
    }

    public static NetworkPolicy defaults() {
        return new NetworkPolicy(
                "Anilib/0.1",
                Optional.empty(),
                Optional.empty(),
                Duration.ofSeconds(30),
                true);
    }

    private static URI proxyUri(URI value) {
        URI uri = normalized(value, "proxy");
        if (!uri.getScheme().equalsIgnoreCase("http")) {
            throw new IllegalArgumentException("proxy must use http");
        }
        if (uri.getQuery() != null || !(uri.getPath().isEmpty() || uri.getPath().equals("/"))) {
            throw new IllegalArgumentException("proxy must be an origin without a path or query");
        }
        return uri;
    }

    private static URI dohUri(URI value) {
        URI uri = normalized(value, "dnsOverHttps");
        if (!uri.getScheme().equalsIgnoreCase("https")) {
            throw new IllegalArgumentException("dnsOverHttps must use https");
        }
        if (uri.getQuery() != null) {
            throw new IllegalArgumentException("dnsOverHttps must not declare a query");
        }
        return uri;
    }

    private static URI normalized(URI value, String name) {
        URI uri = Objects.requireNonNull(value, name + " must not contain null").normalize();
        if (uri.getHost() == null || uri.getHost().isBlank() || uri.getUserInfo() != null
                || uri.getFragment() != null) {
            throw new IllegalArgumentException(name + " must be an absolute origin URI");
        }
        return uri;
    }
}
