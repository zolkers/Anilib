package fr.vriege.anilib.framework.http;

import fr.vriege.anilib.foundation.validation.Preconditions;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** Immutable HTTP response with defensive body and header ownership. */
public final class HttpResponse {
    private final int statusCode;
    private final Map<String, List<String>> headers;
    private final byte[] body;
    private final boolean fromCache;

    public HttpResponse(int statusCode, Map<String, List<String>> headers, byte[] body, boolean fromCache) {
        if (statusCode < 100 || statusCode > 599) {
            throw new IllegalArgumentException("statusCode must be between 100 and 599");
        }
        this.statusCode = statusCode;
        this.headers = immutableHeaders(headers);
        this.body = Preconditions.requireNonNull(body, "body").clone();
        this.fromCache = fromCache;
    }

    public int statusCode() {
        return statusCode;
    }

    public Map<String, List<String>> headers() {
        return headers;
    }

    public Optional<String> firstHeader(String name) {
        List<String> values = headers.get(Preconditions.requireNonBlank(name, "name").toLowerCase(Locale.ROOT));
        return values == null || values.isEmpty() ? Optional.empty() : Optional.of(values.getFirst());
    }

    public byte[] body() {
        return body.clone();
    }

    public String bodyAsUtf8() {
        return new String(body, StandardCharsets.UTF_8);
    }

    public boolean fromCache() {
        return fromCache;
    }

    public HttpResponse asCached() {
        return fromCache ? this : new HttpResponse(statusCode, headers, body, true);
    }

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
