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

/**
 * An immutable, validated HTTP request independent of any platform networking
 * API.
 *
 * <p>Requests support absolute HTTP and HTTPS URIs with a host. User information
 * and fragments are rejected because they must not cross the network boundary.
 * Header names are validated, normalized to lowercase, and stored with all
 * values in insertion order. Hop-by-hop or transport-owned headers such as
 * {@code Host} and {@code Content-Length} cannot be set by callers.</p>
 *
 * <p>The request body is defensively copied on input and output. A body is not
 * permitted for {@link HttpMethod#GET GET} or {@link HttpMethod#HEAD HEAD}, and
 * only GET requests may use a response-cache policy other than
 * {@link HttpCachePolicy#bypass()}.</p>
 *
 * <p>Instances are immutable and safe to share between threads.</p>
 *
 * @see #builder(URI)
 * @see AnilibHttpClient
 * @see HttpTransport
 */
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

    /**
     * Creates a builder for an absolute HTTP or HTTPS URI.
     *
     * <p>Full URI validation occurs when {@link Builder#build()} is called.</p>
     *
     * @param uri the non-null request URI
     * @return a new builder with GET, an empty body, a 30-second timeout, cache
     *         bypass, and no rate-limit interval
     * @throws NullPointerException if {@code uri} is {@code null}
     */
    public static Builder builder(URI uri) {
        return new Builder(uri);
    }

    /**
     * Returns the normalized absolute request URI.
     *
     * @return the HTTP or HTTPS URI
     */
    public URI uri() {
        return uri;
    }

    /**
     * Returns the request method.
     *
     * @return the HTTP method
     */
    public HttpMethod method() {
        return method;
    }

    /**
     * Returns the caller-supplied headers.
     *
     * <p>The returned map and its value lists are immutable. Names are lowercase
     * and each list preserves the order in which values were added.</p>
     *
     * @return the immutable request-header multimap
     */
    public Map<String, List<String>> headers() {
        return headers;
    }

    /**
     * Returns a copy of the request body.
     *
     * @return a new byte array containing the body; never {@code null}
     */
    public byte[] body() {
        return body.clone();
    }

    /**
     * Returns the positive exchange timeout.
     *
     * @return the connection and response timeout
     */
    public Duration timeout() {
        return timeout;
    }

    /**
     * Returns the response-cache policy for this request.
     *
     * @return the cache policy
     */
    public HttpCachePolicy cachePolicy() {
        return cachePolicy;
    }

    /**
     * Returns the minimum interval requested between exchanges to the same
     * origin.
     *
     * @return a non-negative interval; zero disables spacing for this request
     */
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

    /**
     * Incrementally builds an immutable {@link HttpRequest}.
     *
     * <p>A builder may be reused; each call to {@link #build()} captures a new
     * immutable snapshot of its current method, headers, body, timeout, cache
     * policy, and rate-limit interval.</p>
     */
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

        /**
         * Sets the HTTP method.
         *
         * @param value the non-null method
         * @return this builder
         * @throws NullPointerException if {@code value} is {@code null}
         */
        public Builder method(HttpMethod value) {
            method = Preconditions.requireNonNull(value, "method");
            return this;
        }

        /**
         * Appends one request-header value.
         *
         * <p>The header name is normalized to lowercase. Repeated calls with the
         * same normalized name append rather than replace values.</p>
         *
         * @param name  the non-blank HTTP token name
         * @param value the non-null value, without carriage return or line feed
         * @return this builder
         * @throws NullPointerException if {@code name} or {@code value} is
         *                              {@code null}
         * @throws IllegalArgumentException if {@code name} is blank, malformed,
         *                                  or transport-restricted, or if
         *                                  {@code value} contains a line break
         */
        public Builder header(String name, String value) {
            String normalized = validateHeaderName(name);
            headers.computeIfAbsent(normalized, ignored -> new ArrayList<>()).add(validateHeaderValue(value));
            return this;
        }

        /**
         * Replaces the request body with a defensive copy of {@code value}.
         *
         * @param value the non-null body bytes
         * @return this builder
         * @throws NullPointerException if {@code value} is {@code null}
         */
        public Builder body(byte[] value) {
            body = Preconditions.requireNonNull(value, "body").clone();
            return this;
        }

        /**
         * Sets the positive exchange timeout.
         *
         * @param value the strictly positive timeout
         * @return this builder
         * @throws NullPointerException if {@code value} is {@code null}
         * @throws IllegalArgumentException if {@code value} is zero or negative
         */
        public Builder timeout(Duration value) {
            timeout = requirePositive(value, "timeout");
            return this;
        }

        /**
         * Sets the response-cache policy.
         *
         * @param value the non-null cache policy
         * @return this builder
         * @throws NullPointerException if {@code value} is {@code null}
         */
        public Builder cache(HttpCachePolicy value) {
            cachePolicy = Preconditions.requireNonNull(value, "cachePolicy");
            return this;
        }

        /**
         * Requests a minimum interval between exchanges to the same origin.
         *
         * @param value the non-negative interval; zero disables spacing
         * @return this builder
         * @throws NullPointerException if {@code value} is {@code null}
         * @throws IllegalArgumentException if {@code value} is negative
         */
        public Builder minimumInterval(Duration value) {
            minimumInterval = Preconditions.requireNonNull(value, "minimumInterval");
            if (minimumInterval.isNegative()) {
                throw new IllegalArgumentException("minimumInterval must not be negative");
            }
            return this;
        }

        /**
         * Validates the complete request and creates an immutable snapshot.
         *
         * @return a new immutable request
         * @throws IllegalArgumentException if the URI is not an absolute HTTP or
         *                                  HTTPS location with a host, contains
         *                                  user information or a fragment, the
         *                                  method/body combination is invalid,
         *                                  or a non-GET method uses caching
         */
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
