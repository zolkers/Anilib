package fr.vriege.anilib.framework.http.runtime;

import fr.vriege.anilib.framework.concurrent.runtime.ManagedExecutors;
import fr.vriege.anilib.framework.http.HttpException;

import java.io.ByteArrayOutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MediaHeaderProxy implements AutoCloseable {
    private static final int CONNECT_TIMEOUT_MILLIS = 15_000;
    private static final int READ_TIMEOUT_MILLIS = 30_000;
    private static final int SOCKET_TIMEOUT_MILLIS = 35_000;
    private static final int MAXIMUM_LINE_BYTES = 8 * 1024;
    private static final int MAXIMUM_REQUEST_BYTES = 64 * 1024;
    private static final int MAXIMUM_PLAYLIST_BYTES = 8 * 1024 * 1024;
    private static final int MAXIMUM_REDIRECTS = 8;
    private static final int MAXIMUM_TARGETS = 100_000;
    private static final Pattern DOUBLE_QUOTED_URI = Pattern.compile("URI=\"([^\"]+)\"");
    private static final Pattern SINGLE_QUOTED_URI = Pattern.compile("URI='([^']+)'");
    private static final Set<String> BLOCKED_REQUEST_HEADERS = Set.of(
            "accept-encoding",
            "connection",
            "content-length",
            "host",
            "keep-alive",
            "proxy-authenticate",
            "proxy-authorization",
            "te",
            "trailer",
            "transfer-encoding",
            "upgrade");
    private static final Set<String> FORWARDED_RESPONSE_HEADERS = Set.of(
            "accept-ranges",
            "cache-control",
            "content-disposition",
            "content-range",
            "content-type",
            "etag",
            "expires",
            "last-modified");

    private final ServerSocket server;
    private final ExecutorService workers;
    private final JdkHttpCookieJar cookies = new JdkHttpCookieJar();
    private final Map<String, Target> targets = new ConcurrentHashMap<>();
    private final Map<Target, String> routes = new ConcurrentHashMap<>();
    private final AtomicLong nextTarget = new AtomicLong();
    private final String token;
    private final Thread acceptor;
    private volatile boolean closed;

    public MediaHeaderProxy() {
        try {
            server = new ServerSocket();
            server.bind(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 32);
        } catch (IOException exception) {
            throw new HttpException("Unable to start the loopback media relay", exception);
        }
        token = randomToken();
        workers = ManagedExecutors.bounded("anilib-media-relay-worker", 8, 64);
        acceptor = ManagedExecutors.start("anilib-media-relay-acceptor", this::acceptConnections);
    }

    public URI route(URI target, Map<String, String> headers) {
        URI location = requireHttpTarget(target);
        Map<String, String> requestHeaders = immutableHeaders(headers);
        ensureOpen();
        Target descriptor = new Target(location, requestHeaders);
        String id = routes.computeIfAbsent(descriptor, value -> {
            if (targets.size() >= MAXIMUM_TARGETS) {
                throw new HttpException("Media header proxy route limit exceeded");
            }
            String route = Long.toUnsignedString(nextTarget.incrementAndGet(), 36);
            targets.put(route, value);
            return route;
        });
        return URI.create("http://127.0.0.1:" + server.getLocalPort() + "/" + token + "/" + id);
    }

    private void acceptConnections() {
        while (!closed) {
            try {
                Socket socket = server.accept();
                workers.execute(() -> handle(socket));
            } catch (SocketException exception) {
                if (!closed) {
                    close();
                }
            } catch (IOException exception) {
                if (!closed) {
                    close();
                }
            }
        }
    }

    private void handle(Socket socket) {
        try (socket) {
            ResponseOutput output = new ResponseOutput(socket.getOutputStream());
            try {
                socket.setSoTimeout(SOCKET_TIMEOUT_MILLIS);
                Request request = readRequest(socket.getInputStream());
                Target target = target(request.path());
                relay(request, target, output);
            } catch (BadRequestException exception) {
                if (!output.started()) {
                    writeQuietly(output, exception.status(), exception.getMessage());
                }
            } catch (Exception exception) {
                if (!output.started()) {
                    writeQuietly(output, 502, "Media relay failed");
                }
            }
        } catch (IOException ignored) {
            // The media client may disconnect while a response is being written.
        }
    }

    private void relay(Request request, Target target, OutputStream output) throws IOException {
        Upstream upstream = openUpstream(request, target);
        HttpURLConnection connection = upstream.connection();
        try {
            if (upstream.status() >= 200 && upstream.status() < 300
                    && isPlaylist(upstream.location(), connection, request.method())) {
                byte[] original = readBounded(connection.getInputStream(), MAXIMUM_PLAYLIST_BYTES);
                byte[] rewritten = rewritePlaylist(
                        new String(original, StandardCharsets.UTF_8),
                        upstream.location(),
                        target.headers()).getBytes(StandardCharsets.UTF_8);
                writeResponse(output, upstream.status(), playlistHeaders(connection), rewritten, false);
                return;
            }
            boolean head = request.method().equals("HEAD");
            writeResponseHead(output, upstream.status(), responseHeaders(connection), contentLength(connection), head);
            if (!head) {
                InputStream response = upstream.status() >= 400
                        ? connection.getErrorStream()
                        : connection.getInputStream();
                if (response != null) {
                    try (response) {
                        response.transferTo(output);
                    }
                }
            }
            output.flush();
        } finally {
            connection.disconnect();
        }
    }

    private Upstream openUpstream(Request request, Target target) throws IOException {
        URI location = target.location();
        for (int redirect = 0; redirect <= MAXIMUM_REDIRECTS; redirect++) {
            HttpURLConnection connection = (HttpURLConnection) location.toURL().openConnection();
            connection.setConnectTimeout(CONNECT_TIMEOUT_MILLIS);
            connection.setReadTimeout(READ_TIMEOUT_MILLIS);
            connection.setInstanceFollowRedirects(false);
            connection.setUseCaches(false);
            connection.setRequestMethod(request.method());
            applyHeaders(connection, location, request.headers(), target.headers());
            int status = connection.getResponseCode();
            cookies.store(location, connection.getHeaderFields());
            String redirectLocation = connection.getHeaderField("Location");
            if (!isRedirect(status) || redirectLocation == null) {
                return new Upstream(location, connection, status);
            }
            connection.disconnect();
            location = requireHttpTarget(location.resolve(redirectLocation));
        }
        throw new IOException("Media relay exceeded redirect limit");
    }

    private void applyHeaders(
            HttpURLConnection connection,
            URI location,
            Map<String, String> playerHeaders,
            Map<String, String> sourceHeaders) {
        Map<String, String> merged = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        playerHeaders.forEach((name, value) -> {
            if (!BLOCKED_REQUEST_HEADERS.contains(name.toLowerCase(Locale.ROOT))) {
                merged.put(name, value);
            }
        });
        cookies.requestHeaders(location).forEach((name, values) ->
                merged.put(name, String.join("; ", values)));
        sourceHeaders.forEach((name, value) -> {
            if (!BLOCKED_REQUEST_HEADERS.contains(name.toLowerCase(Locale.ROOT))) {
                if (name.equalsIgnoreCase("Cookie") && merged.containsKey(name)) {
                    merged.put(name, value + "; " + merged.get(name));
                } else {
                    merged.put(name, value);
                }
            }
        });
        merged.put("Accept-Encoding", "identity");
        merged.forEach(connection::setRequestProperty);
    }

    private String rewritePlaylist(String playlist, URI base, Map<String, String> headers) {
        String[] lines = playlist.split("\\r?\\n", -1);
        StringBuilder rewritten = new StringBuilder(playlist.length() + 256);
        for (int index = 0; index < lines.length; index++) {
            String line = lines[index];
            String trimmed = line.strip();
            if (!trimmed.isEmpty()) {
                if (trimmed.startsWith("#")) {
                    line = rewriteAttributeUris(line, base, headers);
                } else {
                    String replacement = routeReference(base, trimmed, headers);
                    line = line.replace(trimmed, replacement);
                }
            }
            rewritten.append(line);
            if (index + 1 < lines.length) {
                rewritten.append('\n');
            }
        }
        return rewritten.toString();
    }

    private String rewriteAttributeUris(String line, URI base, Map<String, String> headers) {
        String doubleQuoted = replaceAttributeUris(line, DOUBLE_QUOTED_URI, '"', base, headers);
        return replaceAttributeUris(doubleQuoted, SINGLE_QUOTED_URI, '\'', base, headers);
    }

    private String replaceAttributeUris(
            String line,
            Pattern pattern,
            char quote,
            URI base,
            Map<String, String> headers) {
        Matcher matcher = pattern.matcher(line);
        StringBuffer rewritten = new StringBuffer();
        while (matcher.find()) {
            String replacement = "URI=" + quote + routeReference(base, matcher.group(1), headers) + quote;
            matcher.appendReplacement(rewritten, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(rewritten);
        return rewritten.toString();
    }

    private String routeReference(URI base, String reference, Map<String, String> headers) {
        URI resolved = base.resolve(reference);
        String scheme = resolved.getScheme();
        if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
            return reference;
        }
        return route(resolved, headers).toString();
    }

    private Target target(String path) {
        String prefix = "/" + token + "/";
        if (!path.startsWith(prefix)) {
            throw new BadRequestException(404, "Unknown media relay route");
        }
        String id = path.substring(prefix.length());
        Target target = targets.get(id);
        if (target == null || id.indexOf('/') >= 0) {
            throw new BadRequestException(404, "Unknown media relay target");
        }
        return target;
    }

    private static Request readRequest(InputStream input) throws IOException {
        int[] consumed = {0};
        String requestLine = readLine(input, consumed);
        String[] parts = requestLine.split(" ");
        if (parts.length != 3 || !(parts[0].equals("GET") || parts[0].equals("HEAD"))) {
            throw new BadRequestException(405, "Only GET and HEAD are supported");
        }
        URI requestTarget;
        try {
            requestTarget = URI.create(parts[1]);
        } catch (IllegalArgumentException exception) {
            throw new BadRequestException(400, "Invalid media relay target");
        }
        Map<String, String> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        while (true) {
            String line = readLine(input, consumed);
            if (line.isEmpty()) {
                break;
            }
            int separator = line.indexOf(':');
            if (separator <= 0) {
                throw new BadRequestException(400, "Invalid request header");
            }
            headers.put(line.substring(0, separator).strip(), line.substring(separator + 1).strip());
        }
        return new Request(parts[0], requestTarget.getPath(), Map.copyOf(headers));
    }

    private static String readLine(InputStream input, int[] consumed) throws IOException {
        ByteArrayOutputStream line = new ByteArrayOutputStream();
        while (true) {
            int value = input.read();
            if (value < 0) {
                throw new BadRequestException(400, "Unexpected end of request");
            }
            consumed[0]++;
            if (consumed[0] > MAXIMUM_REQUEST_BYTES || line.size() > MAXIMUM_LINE_BYTES) {
                throw new BadRequestException(431, "Request headers are too large");
            }
            if (value == '\n') {
                byte[] bytes = line.toByteArray();
                int length = bytes.length > 0 && bytes[bytes.length - 1] == '\r'
                        ? bytes.length - 1
                        : bytes.length;
                return new String(bytes, 0, length, StandardCharsets.ISO_8859_1);
            }
            line.write(value);
        }
    }

    private static void writeResponse(
            OutputStream output,
            int status,
            Map<String, String> headers,
            byte[] body,
            boolean head) throws IOException {
        writeResponseHead(output, status, headers, body.length, head);
        if (!head) {
            output.write(body);
        }
        output.flush();
    }

    private static void writeResponseHead(
            OutputStream output,
            int status,
            Map<String, String> headers,
            long length,
            boolean head) throws IOException {
        StringBuilder response = new StringBuilder()
                .append("HTTP/1.1 ").append(status).append(' ').append(reason(status)).append("\r\n")
                .append("Connection: close\r\n");
        headers.forEach((name, value) -> response.append(name).append(": ").append(value).append("\r\n"));
        if (length >= 0) {
            response.append("Content-Length: ").append(length).append("\r\n");
        } else if (head) {
            response.append("Content-Length: 0\r\n");
        }
        response.append("\r\n");
        output.write(response.toString().getBytes(StandardCharsets.ISO_8859_1));
    }

    private static Map<String, String> responseHeaders(HttpURLConnection connection) {
        Map<String, String> headers = new LinkedHashMap<>();
        connection.getHeaderFields().forEach((name, values) -> {
            if (name != null && values != null && !values.isEmpty()
                    && FORWARDED_RESPONSE_HEADERS.contains(name.toLowerCase(Locale.ROOT))) {
                headers.put(name, values.getFirst());
            }
        });
        return Map.copyOf(headers);
    }

    private static Map<String, String> playlistHeaders(HttpURLConnection connection) {
        Map<String, String> headers = new LinkedHashMap<>(responseHeaders(connection));
        headers.keySet().removeIf(name -> name.equalsIgnoreCase("Content-Range")
                || name.equalsIgnoreCase("Accept-Ranges"));
        return Map.copyOf(headers);
    }

    private static long contentLength(HttpURLConnection connection) {
        return connection.getHeaderFieldLong("Content-Length", -1L);
    }

    private static boolean isPlaylist(URI location, HttpURLConnection connection, String method) {
        if (!method.equals("GET")) {
            return false;
        }
        String contentType = Objects.toString(connection.getContentType(), "").toLowerCase(Locale.ROOT);
        String path = Objects.toString(location.getPath(), "").toLowerCase(Locale.ROOT);
        return contentType.contains("mpegurl") || path.endsWith(".m3u8");
    }

    private static boolean isRedirect(int status) {
        return status == 301 || status == 302 || status == 303 || status == 307 || status == 308;
    }

    private static URI requireHttpTarget(URI target) {
        URI value = Objects.requireNonNull(target, "target must not be null");
        String scheme = value.getScheme();
        if (!value.isAbsolute() || scheme == null
                || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
            throw new IllegalArgumentException("target must be an absolute HTTP URI");
        }
        return value;
    }

    private static Map<String, String> immutableHeaders(Map<String, String> values) {
        Objects.requireNonNull(values, "headers must not be null");
        Map<String, String> copy = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        values.forEach((name, value) -> {
            if (name == null || name.isBlank() || name.indexOf('\r') >= 0 || name.indexOf('\n') >= 0) {
                throw new IllegalArgumentException("header name is invalid");
            }
            if (value == null || value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0) {
                throw new IllegalArgumentException("header value is invalid");
            }
            copy.put(name, value);
        });
        return Map.copyOf(copy);
    }

    private static byte[] readBounded(InputStream input, int maximum) throws IOException {
        try (input) {
            byte[] body = input.readNBytes(maximum + 1);
            if (body.length > maximum) {
                throw new IOException("Media playlist exceeds safety limit");
            }
            return body;
        }
    }

    private static String randomToken() {
        byte[] bytes = new byte[24];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String reason(int status) {
        return switch (status) {
            case 200 -> "OK";
            case 206 -> "Partial Content";
            case 400 -> "Bad Request";
            case 401 -> "Unauthorized";
            case 403 -> "Forbidden";
            case 404 -> "Not Found";
            case 405 -> "Method Not Allowed";
            case 416 -> "Range Not Satisfiable";
            case 431 -> "Request Header Fields Too Large";
            case 500 -> "Internal Server Error";
            case 502 -> "Bad Gateway";
            default -> "Upstream Response";
        };
    }

    private static void writeQuietly(OutputStream output, int status, String message) {
        try {
            byte[] body = Objects.toString(message, "Media relay failed").getBytes(StandardCharsets.UTF_8);
            writeResponse(output, status, Map.of("Content-Type", "text/plain"), body, false);
        } catch (IOException ignored) {
            // The media client may already have disconnected.
        }
    }

    private void ensureOpen() {
        if (closed) {
            throw new HttpException("Media header proxy is closed");
        }
    }

    @Override
    public void close() {
        if (!closed) {
            closed = true;
            try {
                server.close();
            } catch (IOException ignored) {
                // Closing an already-failed loopback socket is best effort.
            }
            ManagedExecutors.shutdown(workers);
            targets.clear();
            routes.clear();
        }
    }

    private record Target(URI location, Map<String, String> headers) {
    }

    private record Request(String method, String path, Map<String, String> headers) {
    }

    private record Upstream(URI location, HttpURLConnection connection, int status) {
    }

    private static final class BadRequestException extends RuntimeException {
        private static final long serialVersionUID = 1L;
        private final int status;

        private BadRequestException(int status, String message) {
            super(message);
            this.status = status;
        }

        private int status() {
            return status;
        }
    }

    private static final class ResponseOutput extends FilterOutputStream {
        private boolean started;

        private ResponseOutput(OutputStream output) {
            super(output);
        }

        @Override
        public void write(int value) throws IOException {
            started = true;
            out.write(value);
        }

        @Override
        public void write(byte[] bytes, int offset, int length) throws IOException {
            if (length > 0) {
                started = true;
            }
            out.write(bytes, offset, length);
        }

        private boolean started() {
            return started;
        }
    }
}
