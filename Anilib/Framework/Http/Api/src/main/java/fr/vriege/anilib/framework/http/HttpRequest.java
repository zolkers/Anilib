package fr.vriege.anilib.framework.http;

import fr.vriege.anilib.foundation.validation.Preconditions;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public final class HttpRequest {
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);
    private static final Pattern HEADER_NAME = Pattern.compile("[!#$%&'*+.^_`|~0-9A-Za-z-]+");
    private static final Set<String> RESTRICTED_HEADERS = Set.of(
            "connection", "content-length", "expect", "host", "upgrade");

    private final URI uri;
    private final HttpMethod method;
    private final Map<String, List<String>> headers;
    private final byte[] body;
    private final Duration timeout;
    private final HttpCachePolicy cachePolicy;
    private final Duration minimumInterval;

    private HttpRequest(Builder builder) {
        uri = validateUri(builder.uri);
        method = builder.method;
        headers = immutableHeaders(builder.headers);
        body = builder.body.clone();
        timeout = builder.timeout;
        cachePolicy = builder.cachePolicy;
        minimumInterval = builder.minimumInterval;
        validateCombination();
    }

    public static Builder builder(URI uri) {
        return new Builder(uri);
    }

    public URI uri() {
        return uri;
    }

    public HttpMethod method() {
        return method;
    }

    public Map<String, List<String>> headers() {
        return headers;
    }

    public byte[] body() {
        return body.clone();
    }

    public Duration timeout() {
        return timeout;
    }

    public HttpCachePolicy cachePolicy() {
        return cachePolicy;
    }

    public Duration minimumInterval() {
        return minimumInterval;
    }

    private void validateCombination() {
        if (body.length > 0 && (method == HttpMethod.GET || method == HttpMethod.HEAD)) {
            throw new IllegalArgumentException(method + " requests cannot carry a body");
        }
        if (cachePolicy.mode() != HttpCachePolicy.Mode.BYPASS && method != HttpMethod.GET) {
            throw new IllegalArgumentException("Only GET requests may use the response cache");
        }
    }

    private static URI validateUri(URI value) {
        URI uri = Preconditions.requireNonNull(value, "uri").normalize();
        String scheme = uri.getScheme();
        if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
            throw new IllegalArgumentException("uri must use http or https");
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw new IllegalArgumentException("uri must declare a host");
        }
        if (uri.getUserInfo() != null || uri.getFragment() != null) {
            throw new IllegalArgumentException("uri must not contain user information or a fragment");
        }
        return uri;
    }

    private static Map<String, List<String>> immutableHeaders(Map<String, List<String>> source) {
        Map<String, List<String>> copy = new LinkedHashMap<>();
        source.forEach((name, values) -> copy.put(name, List.copyOf(values)));
        return Map.copyOf(copy);
    }

    public static final class Builder {
        private final URI uri;
        private final Map<String, List<String>> headers = new LinkedHashMap<>();
        private HttpMethod method = HttpMethod.GET;
        private byte[] body = new byte[0];
        private Duration timeout = DEFAULT_TIMEOUT;
        private HttpCachePolicy cachePolicy = HttpCachePolicy.bypass();
        private Duration minimumInterval = Duration.ZERO;

        private Builder(URI uri) {
            this.uri = Preconditions.requireNonNull(uri, "uri");
        }

        public Builder method(HttpMethod value) {
            method = Preconditions.requireNonNull(value, "method");
            return this;
        }

        public Builder header(String name, String value) {
            String normalized = validateHeaderName(name);
            headers.computeIfAbsent(normalized, ignored -> new ArrayList<>()).add(validateHeaderValue(value));
            return this;
        }

        public Builder body(byte[] value) {
            body = Preconditions.requireNonNull(value, "body").clone();
            return this;
        }

        public Builder timeout(Duration value) {
            timeout = requirePositive(value, "timeout");
            return this;
        }

        public Builder cache(HttpCachePolicy value) {
            cachePolicy = Preconditions.requireNonNull(value, "cachePolicy");
            return this;
        }

        public Builder minimumInterval(Duration value) {
            minimumInterval = Preconditions.requireNonNull(value, "minimumInterval");
            if (minimumInterval.isNegative()) {
                throw new IllegalArgumentException("minimumInterval must not be negative");
            }
            return this;
        }

        public HttpRequest build() {
            return new HttpRequest(this);
        }

        private static String validateHeaderName(String value) {
            String name = Preconditions.requireNonBlank(value, "header name").toLowerCase(Locale.ROOT);
            if (!HEADER_NAME.matcher(name).matches() || RESTRICTED_HEADERS.contains(name)) {
                throw new IllegalArgumentException("Invalid or restricted HTTP header: " + name);
            }
            return name;
        }

        private static String validateHeaderValue(String value) {
            String headerValue = Preconditions.requireNonNull(value, "header value");
            if (headerValue.indexOf('\r') >= 0 || headerValue.indexOf('\n') >= 0) {
                throw new IllegalArgumentException("HTTP header values cannot contain line breaks");
            }
            return headerValue;
        }

        private static Duration requirePositive(Duration value, String name) {
            Duration duration = Preconditions.requireNonNull(value, name);
            if (duration.isZero() || duration.isNegative()) {
                throw new IllegalArgumentException(name + " must be positive");
            }
            return duration;
        }
    }
}
