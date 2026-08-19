package fr.vriege.anilib.platform.desktopextensionhost.extension;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;

public final class ExtensionDownloadClient {
    private static final int MAX_REDIRECTS = 5;
    private static final long MAX_APK_BYTES = 256L * 1024L * 1024L;
    private final HttpClient client;

    public ExtensionDownloadClient() {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build());
    }

    ExtensionDownloadClient(HttpClient client) {
        this.client = Objects.requireNonNull(client, "client");
    }

    public Path download(URI source, Path temporaryDirectory) {
        URI uri = requireHttps(source);
        Path directory = temporaryDirectory.toAbsolutePath().normalize();
        try {
            Files.createDirectories(directory);
            Path destination = Files.createTempFile(directory, ".anilib-download-", ".apk");
            try {
                download(uri, destination, 0);
                return destination;
            } catch (RuntimeException failure) {
                Files.deleteIfExists(destination);
                throw failure;
            }
        } catch (IOException exception) {
            throw new UncheckedIOException("Unable to create extension download", exception);
        }
    }

    private void download(URI uri, Path destination, int redirects) {
        if (redirects > MAX_REDIRECTS) {
            throw new IllegalStateException("Extension download exceeded the redirect limit");
        }
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(90))
                .header("Accept", "application/vnd.android.package-archive, application/octet-stream")
                .GET()
                .build();
        try {
            HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream body = response.body()) {
                if (response.statusCode() >= 300 && response.statusCode() < 400) {
                    String location = response.headers().firstValue("location")
                            .orElseThrow(() -> new IllegalStateException("Extension redirect has no location"));
                    download(requireHttps(uri.resolve(location)), destination, redirects + 1);
                    return;
                }
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    throw new IllegalStateException("Extension download failed with HTTP " + response.statusCode());
                }
                long declared = response.headers().firstValueAsLong("content-length").orElse(-1);
                if (declared == 0 || declared > MAX_APK_BYTES) {
                    throw new IllegalStateException("Extension download size is outside the accepted range");
                }
                copyBounded(body, destination);
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Extension download was interrupted", exception);
        } catch (IOException exception) {
            throw new UncheckedIOException("Unable to download extension", exception);
        }
    }

    private static void copyBounded(InputStream input, Path destination) throws IOException {
        long copied = 0;
        byte[] buffer = new byte[16 * 1024];
        try (OutputStream output = Files.newOutputStream(destination)) {
            int count;
            while ((count = input.read(buffer)) >= 0) {
                copied += count;
                if (copied > MAX_APK_BYTES) {
                    throw new IllegalStateException("Extension download exceeds the size limit");
                }
                output.write(buffer, 0, count);
            }
        }
        if (copied == 0) {
            throw new IllegalStateException("Extension download is empty");
        }
    }

    private static URI requireHttps(URI value) {
        URI uri = Objects.requireNonNull(value, "source").normalize();
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                || uri.getUserInfo() != null || uri.getFragment() != null) {
            throw new IllegalArgumentException("Extension location must be an HTTPS URI");
        }
        return uri;
    }
}
