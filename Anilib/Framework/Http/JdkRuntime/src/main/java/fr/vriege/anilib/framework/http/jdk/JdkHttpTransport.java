package fr.vriege.anilib.framework.http.jdk;

import fr.vriege.anilib.framework.http.HttpException;
import fr.vriege.anilib.framework.http.HttpRequest;
import fr.vriege.anilib.framework.http.HttpResponse;
import fr.vriege.anilib.framework.http.HttpTransport;

import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.List;
import java.util.Map;

/** Desktop transport backed by the Java 21 HTTP client with HTTP/2 support. */
public final class JdkHttpTransport implements HttpTransport {
    private static final int MAX_RESPONSE_BYTES = 16 * 1024 * 1024;

    private final java.net.http.HttpClient transport;

    public JdkHttpTransport() {
        transport = java.net.http.HttpClient.newBuilder()
                .followRedirects(java.net.http.HttpClient.Redirect.NEVER)
                .version(java.net.http.HttpClient.Version.HTTP_2)
                .build();
    }

    @Override
    public HttpResponse exchange(HttpRequest request, Map<String, List<String>> headers) {
        java.net.http.HttpRequest networkRequest = networkRequest(request, headers);
        try {
            java.net.http.HttpResponse<InputStream> response =
                    transport.send(networkRequest, BodyHandlers.ofInputStream());
            try (InputStream body = response.body()) {
                byte[] bytes = body.readNBytes(MAX_RESPONSE_BYTES + 1);
                if (bytes.length > MAX_RESPONSE_BYTES) {
                    throw new HttpException("HTTP response exceeds the 16 MiB safety limit");
                }
                return new HttpResponse(response.statusCode(), response.headers().map(), bytes, false);
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new HttpException("HTTP request was interrupted", exception);
        } catch (IOException exception) {
            throw new HttpException("HTTP request failed", exception);
        }
    }

    private static java.net.http.HttpRequest networkRequest(
            HttpRequest request,
            Map<String, List<String>> headers) {
        java.net.http.HttpRequest.Builder builder = java.net.http.HttpRequest.newBuilder(request.uri())
                .timeout(request.timeout());
        headers.forEach((name, values) -> values.forEach(value -> builder.header(name, value)));
        byte[] body = request.body();
        builder.method(
                request.method().name(),
                body.length == 0 ? BodyPublishers.noBody() : BodyPublishers.ofByteArray(body));
        return builder.build();
    }
}
