package fr.vriege.anilib.framework.http.runtime;

import fr.vriege.anilib.framework.http.AnilibHttpClient;
import fr.vriege.anilib.framework.http.HttpCacheEntry;
import fr.vriege.anilib.framework.http.HttpCachePolicy;
import fr.vriege.anilib.framework.http.HttpCookieJar;
import fr.vriege.anilib.framework.http.HttpRateLimiter;
import fr.vriege.anilib.framework.http.HttpRequest;
import fr.vriege.anilib.framework.http.HttpResponse;
import fr.vriege.anilib.framework.http.HttpResponseCache;
import fr.vriege.anilib.framework.http.HttpTransport;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** Shared HTTP policy engine layered over one platform-owned network transport. */
public final class DefaultAnilibHttpClient implements AnilibHttpClient {
    private static final HexFormat HEX = HexFormat.of();

    private final HttpTransport transport;
    private final HttpCookieJar cookies;
    private final HttpResponseCache cache;
    private final HttpRateLimiter rateLimiter;

    public DefaultAnilibHttpClient(
            HttpTransport transport,
            HttpCookieJar cookies,
            HttpResponseCache cache,
            HttpRateLimiter rateLimiter) {
        this.transport = Objects.requireNonNull(transport, "transport must not be null");
        this.cookies = Objects.requireNonNull(cookies, "cookies must not be null");
        this.cache = Objects.requireNonNull(cache, "cache must not be null");
        this.rateLimiter = Objects.requireNonNull(rateLimiter, "rateLimiter must not be null");
    }

    @Override
    public HttpResponse execute(HttpRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        Map<String, List<String>> headers = mergedHeaders(request);
        String cacheKey = cacheKey(request, headers);
        HttpCachePolicy policy = request.cachePolicy();
        if (policy.reads()) {
            HttpCacheEntry cached = cache.find(cacheKey).orElse(null);
            if (cached != null && cached.isFresh(Instant.now())) {
                return cached.response().asCached();
            }
            if (cached != null) {
                cache.invalidate(cacheKey);
            }
        }

        rateLimiter.acquire(request.uri(), request.minimumInterval());
        HttpResponse response = transport.exchange(request, headers).asNetworkResponse();
        cookies.store(request.uri(), response.headers());
        if (policy.writes() && response.statusCode() >= 200 && response.statusCode() < 300) {
            cache.store(cacheKey, new HttpCacheEntry(response, Instant.now().plus(policy.timeToLive())));
        }
        return response;
    }

    private Map<String, List<String>> mergedHeaders(HttpRequest request) {
        Map<String, List<String>> headers = new LinkedHashMap<>();
        request.headers().forEach((name, values) -> headers.put(name, new ArrayList<>(values)));
        cookies.requestHeaders(request.uri()).forEach((name, values) -> {
            String normalized = name.toLowerCase(Locale.ROOT);
            headers.computeIfAbsent(normalized, ignored -> new ArrayList<>()).addAll(values);
        });
        headers.computeIfAbsent("user-agent", ignored -> new ArrayList<>()).add("Anilib/0.1");
        Map<String, List<String>> immutable = new LinkedHashMap<>();
        headers.forEach((name, values) -> immutable.put(name, List.copyOf(values)));
        return Map.copyOf(immutable);
    }

    private static String cacheKey(HttpRequest request, Map<String, List<String>> headers) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            update(digest, request.method().name());
            update(digest, request.uri().toASCIIString());
            headers.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(header -> {
                        update(digest, header.getKey());
                        header.getValue().stream().sorted(Comparator.naturalOrder())
                                .forEach(value -> update(digest, value));
                    });
            return HEX.formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JDK does not provide SHA-256", exception);
        }
    }

    private static void update(MessageDigest digest, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        digest.update((byte) (bytes.length >>> 24));
        digest.update((byte) (bytes.length >>> 16));
        digest.update((byte) (bytes.length >>> 8));
        digest.update((byte) bytes.length);
        digest.update(bytes);
    }
}
