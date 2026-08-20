package fr.vriege.anilib.framework.http.runtime;

import fr.vriege.anilib.framework.http.HttpException;
import fr.vriege.anilib.framework.http.HttpRequest;
import fr.vriege.anilib.framework.http.HttpResponse;
import fr.vriege.anilib.framework.http.HttpTransport;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import java.io.OutputStream;

public final class UrlConnectionHttpTransport implements HttpTransport {
    private static final int MAX_RESPONSE_BYTES = 16 * 1024 * 1024;

    private final Supplier<Optional<URI>> proxy;

    public UrlConnectionHttpTransport() {
        this(Optional::empty);
    }

    public UrlConnectionHttpTransport(Supplier<Optional<URI>> proxy) {
        this.proxy = Objects.requireNonNull(proxy, "proxy must not be null");
    }

    @Override
    public HttpResponse exchange(HttpRequest request, Map<String, List<String>> headers) {
        HttpURLConnection connection = open(request);
        try {
            headers.forEach((name, values) ->
                    values.forEach(value -> connection.addRequestProperty(name, value)));
            byte[] requestBody = request.body();
            if (requestBody.length > 0) {
                connection.setDoOutput(true);
                connection.setFixedLengthStreamingMode(requestBody.length);
                try (OutputStream output = connection.getOutputStream()) {
                    output.write(requestBody);
                }
            }
            int status = connection.getResponseCode();
            Map<String, List<String>> responseHeaders = responseHeaders(connection);
            byte[] body = readBody(connection, status);
            return new HttpResponse(status, responseHeaders, body, false);
        } catch (IOException exception) {
            throw new HttpException("HTTP URL connection failed", exception);
        } finally {
            connection.disconnect();
        }
    }

    private HttpURLConnection open(HttpRequest request) {
        try {
            Optional<URI> configured = Objects.requireNonNull(proxy.get(), "proxy supplied null");
            HttpURLConnection connection = configured.isPresent()
                    ? (HttpURLConnection) request.uri().toURL().openConnection(asProxy(configured.orElseThrow()))
                    : (HttpURLConnection) request.uri().toURL().openConnection();
            connection.setRequestMethod(request.method().name());
            connection.setConnectTimeout(timeoutMillis(request));
            connection.setReadTimeout(timeoutMillis(request));
            connection.setInstanceFollowRedirects(false);
            connection.setUseCaches(false);
            return connection;
        } catch (IOException exception) {
            throw new HttpException("Unable to open an HTTP URL connection", exception);
        }
    }

    private static Proxy asProxy(URI uri) {
        int port = uri.getPort() < 0 ? 80 : uri.getPort();
        return new Proxy(Proxy.Type.HTTP, InetSocketAddress.createUnresolved(uri.getHost(), port));
    }

    private static int timeoutMillis(HttpRequest request) {
        long millis = request.timeout().toMillis();
        return millis > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) millis;
    }

    private static Map<String, List<String>> responseHeaders(HttpURLConnection connection) {
        Map<String, List<String>> headers = new LinkedHashMap<>();
        connection.getHeaderFields().forEach((name, values) -> {
            if (name != null && values != null) {
                headers.put(name, List.copyOf(values));
            }
        });
        return Map.copyOf(headers);
    }

    private static byte[] readBody(HttpURLConnection connection, int status) throws IOException {
        InputStream stream = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
        if (stream == null) {
            return new byte[0];
        }
        try (InputStream input = stream) {
            byte[] bytes = input.readNBytes(MAX_RESPONSE_BYTES + 1);
            if (bytes.length > MAX_RESPONSE_BYTES) {
                throw new HttpException("HTTP response exceeds the 16 MiB safety limit");
            }
            return bytes;
        }
    }
}
