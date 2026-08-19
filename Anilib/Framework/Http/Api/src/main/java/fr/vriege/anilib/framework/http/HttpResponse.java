package fr.vriege.anilib.framework.http;

import fr.vriege.anilib.foundation.validation.Preconditions;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * An immutable HTTP response with explicit cache provenance.
 *
 * <p>Status codes from {@code 100} through {@code 599} are represented without
 * converting non-success codes into exceptions. Header names are normalized to
 * lowercase and both the header multimap and body bytes are defensively owned.
 * The {@link #fromCache()} marker describes how this response was obtained; it
 * is not protocol metadata.</p>
 *
 * <p>Instances are immutable and safe to share between threads.</p>
 */
public final class HttpResponse {
    private final int statusCode;
    private final Map<String, List<String>> headers;
    private final byte[] body;
    private final boolean fromCache;

    /**
     * Creates an immutable response snapshot.
     *
     * @param statusCode the HTTP status code from {@code 100} through {@code 599}
     * @param headers    the non-null response-header multimap
     * @param body       the non-null response body bytes
     * @param fromCache  whether the response was served from a response cache
     * @throws NullPointerException if the header map, a header name, a header
     *                              value list, an element of a value list, or
     *                              {@code body} is {@code null}
     * @throws IllegalArgumentException if {@code statusCode} is outside the
     *                                  supported range or a header name is blank
     */
    public HttpResponse(int statusCode, Map<String, List<String>> headers, byte[] body, boolean fromCache) {
        if (statusCode < 100 || statusCode > 599) {
            throw new IllegalArgumentException("statusCode must be between 100 and 599");
        }
        this.statusCode = statusCode;
        this.headers = immutableHeaders(headers);
        this.body = Preconditions.requireNonNull(body, "body").clone();
        this.fromCache = fromCache;
    }

    /**
     * Returns the HTTP status code.
     *
     * @return a value from {@code 100} through {@code 599}
     */
    public int statusCode() {
        return statusCode;
    }

    /**
     * Returns the immutable, lowercase response-header multimap.
     *
     * @return the response headers
     */
    public Map<String, List<String>> headers() {
        return headers;
    }

    /**
     * Finds the first value of a response header without regard to name case.
     *
     * @param name the non-null, non-blank header name
     * @return the first value, or an empty optional if the header is absent or
     *         has no values
     * @throws NullPointerException if {@code name} is {@code null}
     * @throws IllegalArgumentException if {@code name} is blank
     */
    public Optional<String> firstHeader(String name) {
        List<String> values = headers.get(Preconditions.requireNonBlank(name, "name").toLowerCase(Locale.ROOT));
        return values == null || values.isEmpty() ? Optional.empty() : Optional.of(values.getFirst());
    }

    /**
     * Returns a copy of the response body.
     *
     * @return a new byte array containing the body
     */
    public byte[] body() {
        return body.clone();
    }

    /**
     * Decodes the response body as UTF-8 without content-type inference.
     *
     * @return the UTF-8 decoded body
     */
    public String bodyAsUtf8() {
        return new String(body, StandardCharsets.UTF_8);
    }

    /**
     * Reports whether this response was served from a response cache.
     *
     * @return {@code true} for cache provenance, otherwise {@code false}
     */
    public boolean fromCache() {
        return fromCache;
    }

    /**
     * Returns an equivalent response marked as served from cache.
     *
     * @return this instance if already cached, otherwise an equivalent cached
     *         response
     */
    public HttpResponse asCached() {
        return fromCache ? this : new HttpResponse(statusCode, headers, body, true);
    }

    /**
     * Returns an equivalent response marked as received from the network.
     *
     * @return this instance if already a network response, otherwise an
     *         equivalent network response
     */
    public HttpResponse asNetworkResponse() {
        return fromCache ? new HttpResponse(statusCode, headers, body, false) : this;
    }

    private static Map<String, List<String>> immutableHeaders(Map<String, List<String>> source) {
        Preconditions.requireNonNull(source, "headers");
        Map<String, List<String>> copy = new LinkedHashMap<>();
        source.forEach((name, values) -> {
            String normalized = Preconditions.requireNonBlank(name, "header name").toLowerCase(Locale.ROOT);
            copy.put(normalized, List.copyOf(Preconditions.requireNonNull(values, "header values")));
        });
        return Map.copyOf(copy);
    }
}
