package fr.vriege.anilib.tooling.archtests;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import fr.vriege.anilib.framework.http.AnilibHttpClient;
import fr.vriege.anilib.framework.http.HttpCacheEntry;
import fr.vriege.anilib.framework.http.HttpCachePolicy;
import fr.vriege.anilib.framework.http.HttpMethod;
import fr.vriege.anilib.framework.http.HttpRequest;
import fr.vriege.anilib.framework.http.HttpResponse;
import fr.vriege.anilib.framework.http.jdk.JdkHttpTransport;
import fr.vriege.anilib.framework.http.runtime.DefaultAnilibHttpClient;
import fr.vriege.anilib.framework.http.runtime.FileHttpResponseCache;
import fr.vriege.anilib.framework.http.runtime.HostHttpRateLimiter;
import fr.vriege.anilib.framework.http.runtime.JdkHttpCookieJar;
import fr.vriege.anilib.framework.http.runtime.MediaHeaderProxy;
import fr.vriege.anilib.framework.http.runtime.UrlConnectionHttpTransport;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

final class HttpFrameworkTest {
    private HttpFrameworkTest() {
    }

    static int run() {
        Counter counter = new Counter();
        validatesRequestBoundary(counter);
        persistsBoundedCacheEntries(counter);
        managesCookies(counter);
        enforcesHostIntervals(counter);
        executesAndCachesRealJdkRequests(counter);
        relaysProtectedMediaAndHlsReferences(counter);
        return counter.value;
    }

    private static void validatesRequestBoundary(Counter counter) {
        counter.expectIllegalArgument(
                () -> HttpRequest.builder(URI.create("ftp://example.test/file")).build(),
                "requests must reject non-HTTP schemes");
        counter.expectIllegalArgument(
                () -> HttpRequest.builder(URI.create("https://example.test"))
                        .body(new byte[] {1})
                        .build(),
                "GET requests must reject bodies");
        counter.expectIllegalArgument(
                () -> HttpRequest.builder(URI.create("https://example.test"))
                        .method(HttpMethod.POST)
                        .cache(HttpCachePolicy.preferCache(Duration.ofMinutes(1)))
                        .build(),
                "non-GET requests must reject cache policies");
        counter.expectIllegalArgument(
                () -> HttpRequest.builder(URI.create("https://example.test"))
                        .header("Host", "elsewhere.test"),
                "requests must reject restricted headers");
        counter.expectIllegalArgument(
                () -> HttpRequest.builder(URI.create("https://example.test"))
                        .header("X-Test", "safe\r\ninjected"),
                "requests must reject header injection");

        byte[] body = "owned".getBytes(StandardCharsets.UTF_8);
        HttpRequest request = HttpRequest.builder(URI.create("https://example.test"))
                .method(HttpMethod.POST)
                .body(body)
                .build();
        body[0] = 0;
        counter.check(request.body()[0] == 'o', "requests must defensively own body bytes");
    }

    private static void persistsBoundedCacheEntries(Counter counter) {
        Path directory = temporaryDirectory("anilib-http-cache-test");
        try {
            FileHttpResponseCache first = new FileHttpResponseCache(directory);
            HttpResponse response = new HttpResponse(
                    200,
                    Map.of("content-type", List.of("text/plain")),
                    "cached".getBytes(StandardCharsets.UTF_8),
                    false);
            first.store("stable-key", new HttpCacheEntry(response, Instant.now().plusSeconds(60)));

            FileHttpResponseCache reopened = new FileHttpResponseCache(directory);
            HttpCacheEntry restored = reopened.find("stable-key").orElseThrow();
            counter.check(restored.response().bodyAsUtf8().equals("cached"),
                    "file cache must survive reopening");
            counter.check(restored.response().firstHeader("Content-Type").orElseThrow().equals("text/plain"),
                    "file cache must preserve normalized headers");
            reopened.invalidate("stable-key");
            counter.check(reopened.find("stable-key").isEmpty(),
                    "cache invalidation must remove one opaque key");
            reopened.store("expired", new HttpCacheEntry(response, Instant.now().minusSeconds(1)));
            counter.check(reopened.find("expired").isEmpty(),
                    "expired cache entries must be discarded");
        } finally {
            deleteTree(directory);
        }
    }

    private static void managesCookies(Counter counter) {
        JdkHttpCookieJar cookies = new JdkHttpCookieJar();
        URI uri = URI.create("https://example.test/reader");
        cookies.store(uri, Map.of("set-cookie", List.of("session=abc; Path=/; HttpOnly")));
        String requestCookies = cookies.requestHeaders(uri).values().stream()
                .flatMap(List::stream)
                .reduce("", (left, right) -> left + right);
        counter.check(requestCookies.contains("session=abc"),
                "cookie jar must apply accepted response cookies");
        cookies.clear();
        counter.check(cookies.requestHeaders(uri).isEmpty(),
                "cookie jar clear must remove stored cookies");
    }

    private static void enforcesHostIntervals(Counter counter) {
        HostHttpRateLimiter limiter = new HostHttpRateLimiter();
        URI uri = URI.create("https://example.test/resource");
        Duration interval = Duration.ofMillis(35);
        limiter.acquire(uri, interval);
        long started = System.nanoTime();
        limiter.acquire(uri, interval);
        long elapsed = System.nanoTime() - started;
        counter.check(elapsed >= Duration.ofMillis(15).toNanos(),
                "rate limiter must delay a consecutive request to the same origin");

        long independentStarted = System.nanoTime();
        limiter.acquire(URI.create("https://other.test/resource"), interval);
        long independentElapsed = System.nanoTime() - independentStarted;
        counter.check(independentElapsed < Duration.ofMillis(25).toNanos(),
                "rate limiter must keep different origins independent");
    }

    private static void executesAndCachesRealJdkRequests(Counter counter) {
        Path directory = temporaryDirectory("anilib-http-client-test");
        AtomicInteger cacheRequests = new AtomicInteger();
        HttpServer server = startServer(cacheRequests);
        try {
            URI root = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
            JdkHttpCookieJar cookies = new JdkHttpCookieJar();
            AnilibHttpClient client = new DefaultAnilibHttpClient(
                    new JdkHttpTransport(),
                    cookies,
                    new FileHttpResponseCache(directory),
                    new HostHttpRateLimiter());

            HttpResponse issued = client.execute(HttpRequest.builder(root.resolve("/cookie")).build());
            HttpResponse accepted = client.execute(HttpRequest.builder(root.resolve("/cookie")).build());
            counter.check(issued.bodyAsUtf8().equals("issued"),
                    "JDK client must expose the first network response");
            counter.check(accepted.bodyAsUtf8().equals("accepted"),
                    "JDK client must send cookies on subsequent requests");

            HttpCachePolicy prefer = HttpCachePolicy.preferCache(Duration.ofMinutes(1));
            HttpRequest cachedRequest = HttpRequest.builder(root.resolve("/cache")).cache(prefer).build();
            HttpResponse network = client.execute(cachedRequest);
            HttpResponse cached = client.execute(cachedRequest);
            counter.check(network.bodyAsUtf8().equals("response-1") && !network.fromCache(),
                    "first cacheable request must use the network");
            counter.check(cached.bodyAsUtf8().equals("response-1") && cached.fromCache(),
                    "second cacheable request must use the retained response");
            counter.check(cacheRequests.get() == 1,
                    "cache hit must avoid another network exchange");

            HttpRequest refreshRequest = HttpRequest.builder(root.resolve("/cache"))
                    .cache(HttpCachePolicy.refresh(Duration.ofMinutes(1)))
                    .build();
            HttpResponse refreshed = client.execute(refreshRequest);
            counter.check(refreshed.bodyAsUtf8().equals("response-2") && !refreshed.fromCache(),
                    "refresh policy must replace a cached response from the network");
            counter.check(client.execute(cachedRequest).bodyAsUtf8().equals("response-2"),
                    "refreshed response must become the next preferred cache value");
            HttpResponse jdkRedirect = client.execute(
                    HttpRequest.builder(root.resolve("/redirect")).build());
            counter.check(jdkRedirect.statusCode() == 302,
                    "JDK transport must expose redirects for source origin authorization");

            AnilibHttpClient portableClient = new DefaultAnilibHttpClient(
                    new UrlConnectionHttpTransport(),
                    new JdkHttpCookieJar(),
                    new FileHttpResponseCache(directory.resolve("portable")),
                    new HostHttpRateLimiter());
            HttpResponse portable = portableClient.execute(
                    HttpRequest.builder(root.resolve("/cache")).build());
            counter.check(portable.bodyAsUtf8().equals("response-3"),
                    "URL connection transport must execute the Android-compatible path");
            HttpResponse portableRedirect = portableClient.execute(
                    HttpRequest.builder(root.resolve("/redirect")).build());
            counter.check(portableRedirect.statusCode() == 302,
                    "Android-compatible transport must not bypass source origin authorization");
        } finally {
            server.stop(0);
            deleteTree(directory);
        }
    }

    private static HttpServer startServer(AtomicInteger cacheRequests) {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/cookie", exchange -> {
                boolean accepted = exchange.getRequestHeaders().getOrDefault("Cookie", List.of()).stream()
                        .anyMatch(value -> value.contains("session=abc"));
                if (!accepted) {
                    exchange.getResponseHeaders().add("Set-Cookie", "session=abc; Path=/; HttpOnly");
                }
                respond(exchange, accepted ? "accepted" : "issued");
            });
            server.createContext("/cache", exchange ->
                    respond(exchange, "response-" + cacheRequests.incrementAndGet()));
            server.createContext("/redirect", exchange -> {
                exchange.getResponseHeaders().add("Location", "/cache");
                exchange.sendResponseHeaders(302, -1);
                exchange.close();
            });
            server.start();
            return server;
        } catch (IOException exception) {
            throw new AssertionError("Unable to start the local HTTP contract server", exception);
        }
    }

    private static void relaysProtectedMediaAndHlsReferences(Counter counter) {
        AtomicInteger authorizedRequests = new AtomicInteger();
        AtomicInteger rangedRequests = new AtomicInteger();
        HttpServer upstream = startMediaServer(authorizedRequests, rangedRequests);
        try (MediaHeaderProxy proxy = new MediaHeaderProxy()) {
            URI mediaRoot = URI.create("http://127.0.0.1:" + upstream.getAddress().getPort() + "/media/");
            URI routed = proxy.route(
                    mediaRoot.resolve("master.m3u8"),
                    Map.of("Referer", "https://source.example/", "Cookie", "session=protected"));
            ProxyResponse playlist = fetch(routed, Map.of());
            counter.check(playlist.status() == 200 && playlist.contentType().contains("mpegurl"),
                    "media relay must preserve playlist status and content type");
            String segmentRoute = playlist.body().lines()
                    .filter(line -> !line.isBlank() && !line.startsWith("#"))
                    .findFirst()
                    .orElseThrow();
            int keyStart = playlist.body().indexOf("URI=\"") + "URI=\"".length();
            int keyEnd = playlist.body().indexOf('"', keyStart);
            String keyRoute = playlist.body().substring(keyStart, keyEnd);
            counter.check(segmentRoute.startsWith("http://127.0.0.1:")
                            && keyRoute.startsWith("http://127.0.0.1:"),
                    "media relay must rewrite HLS segments and key attributes to private routes");
            ProxyResponse segment = fetch(URI.create(segmentRoute), Map.of("Range", "bytes=0-"));
            ProxyResponse key = fetch(URI.create(keyRoute), Map.of());
            counter.check(rangedRequests.get() == 1,
                    "media relay must forward player byte-range requests");
            counter.check(segment.body().equals("segment") && key.body().equals("key"),
                    "rewritten HLS routes must return their original upstream resources");
            counter.check(authorizedRequests.get() == 3,
                    "stream headers must be inherited by playlists, segments, and encryption keys");
        } finally {
            upstream.stop(0);
        }
    }

    private static HttpServer startMediaServer(
            AtomicInteger authorizedRequests,
            AtomicInteger rangedRequests) {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/media/", exchange -> {
                String path = exchange.getRequestURI().getPath();
                String cookie = exchange.getRequestHeaders().getFirst("Cookie");
                boolean authorized = "https://source.example/".equals(
                        exchange.getRequestHeaders().getFirst("Referer"))
                        && cookie != null
                        && cookie.contains("session=protected")
                        && (path.endsWith("master.m3u8") || cookie.contains("media=granted"));
                if (authorized) {
                    authorizedRequests.incrementAndGet();
                }
                if (exchange.getRequestHeaders().containsKey("Range")) {
                    rangedRequests.incrementAndGet();
                }
                if (path.endsWith("master.m3u8")) {
                    exchange.getResponseHeaders().set("Content-Type", "application/vnd.apple.mpegurl");
                    exchange.getResponseHeaders().add("Set-Cookie", "media=granted; Path=/media");
                    respond(exchange, "#EXTM3U\n#EXT-X-KEY:METHOD=AES-128,URI=\"key.bin\"\nsegment.ts\n");
                } else if (path.endsWith("key.bin")) {
                    respond(exchange, "key");
                } else {
                    respond(exchange, "segment");
                }
            });
            server.start();
            return server;
        } catch (IOException exception) {
            throw new AssertionError("Unable to start the protected media test server", exception);
        }
    }

    private static ProxyResponse fetch(URI uri, Map<String, String> headers) {
        try {
            HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection();
            connection.setConnectTimeout(5_000);
            connection.setReadTimeout(5_000);
            headers.forEach(connection::setRequestProperty);
            int status = connection.getResponseCode();
            String contentType = java.util.Objects.toString(connection.getContentType(), "");
            String body;
            try (java.io.InputStream input = connection.getInputStream()) {
                body = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            } finally {
                connection.disconnect();
            }
            return new ProxyResponse(status, contentType, body);
        } catch (IOException exception) {
            throw new AssertionError("Unable to fetch a media relay test route", exception);
        }
    }

    private static void respond(HttpExchange exchange, String value) throws IOException {
        byte[] response = value.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(200, response.length);
        try (java.io.OutputStream output = exchange.getResponseBody()) {
            output.write(response);
        }
    }

    private static Path temporaryDirectory(String prefix) {
        try {
            return Files.createTempDirectory(prefix);
        } catch (IOException exception) {
            throw new AssertionError("Unable to create an HTTP test directory", exception);
        }
    }

    private static void deleteTree(Path directory) {
        try (Stream<Path> paths = Files.walk(directory)) {
            for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        } catch (IOException exception) {
            throw new AssertionError("Unable to clean HTTP test directory " + directory, exception);
        }
    }

    private static final class Counter {
        private int value;

        private void check(boolean condition, String message) {
            value++;
            if (!condition) {
                throw new AssertionError(message);
            }
        }

        private void expectIllegalArgument(Runnable action, String message) {
            try {
                action.run();
                throw new AssertionError(message);
            } catch (IllegalArgumentException expected) {
                value++;
            }
        }
    }

    private record ProxyResponse(int status, String contentType, String body) {
    }
}
