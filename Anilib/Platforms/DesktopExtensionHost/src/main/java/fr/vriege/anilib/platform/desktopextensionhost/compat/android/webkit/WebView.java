package fr.vriege.anilib.platform.desktopextensionhost.compat.android.webkit;

import fr.vriege.anilib.platform.desktopextensionhost.compat.android.content.Context;
import fr.vriege.anilib.platform.desktopextensionhost.compat.android.net.Uri;
import fr.vriege.anilib.platform.desktopextensionhost.compat.android.view.View;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

public class WebView extends View {
    private static final int MAX_DOCUMENT_BYTES = 2 * 1024 * 1024;
    private static final int MAX_MEDIA_CANDIDATES = 64;
    private static final Pattern MEDIA_LOCATION = Pattern.compile(
            "(?i)(?:https?://|(?:\\.\\.?/|/))[^\\s\\\"'<>]+\\.(?:m3u8|mp4|mpd)(?:\\?[^\\s\\\"'<>]*)?");
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private final Context context;
    private final WebSettings settings = new WebSettings();
    private WebViewClient client = new WebViewClient();
    private boolean destroyed;
    private boolean stopped;

    public WebView(Context context) {
        this.context = Objects.requireNonNull(context, "context");
    }

    public WebSettings getSettings() {
        return settings;
    }

    public void setWebViewClient(WebViewClient value) {
        client = Objects.requireNonNull(value, "value");
    }

    public void loadUrl(String url, Map<String, String> headers) {
        requireActive();
        stopped = false;
        URI page = webLocation(url);
        notifyRequest(page);
        if (stopped || mediaLocation(page)) {
            return;
        }
        inspectDocument(page, headers == null ? Map.of() : headers);
    }

    public void stopLoading() {
        stopped = true;
    }

    public void destroy() {
        stopped = true;
        destroyed = true;
    }

    public Context getContext() {
        return context;
    }

    private void inspectDocument(URI page, Map<String, String> headers) {
        try {
            HttpRequest.Builder request = HttpRequest.newBuilder(page).timeout(Duration.ofSeconds(8)).GET();
            headers.forEach((name, value) -> addHeader(request, name, value));
            if (settings.getUserAgentString() != null && !headersContain(headers, "User-Agent")) {
                addHeader(request, "User-Agent", settings.getUserAgentString());
            }
            HttpResponse<InputStream> response = HTTP.send(request.build(), HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream body = response.body()) {
                if (response.statusCode() < 200 || response.statusCode() >= 400) {
                    return;
                }
                byte[] document = body.readNBytes(MAX_DOCUMENT_BYTES + 1);
                if (document.length > MAX_DOCUMENT_BYTES) {
                    return;
                }
                notifyMediaLocations(page, new String(document, StandardCharsets.UTF_8));
            }
        } catch (IOException ignored) {
            // A failed embed must not make the whole extension incompatible.
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        } catch (IllegalArgumentException ignored) {
            // A malformed embed must not make the whole extension incompatible.
        }
    }

    private void notifyMediaLocations(URI page, String document) {
        String normalized = document.replace("\\/", "/").replace("&amp;", "&");
        var matches = MEDIA_LOCATION.matcher(normalized);
        var candidates = new LinkedHashSet<URI>();
        while (matches.find() && candidates.size() < MAX_MEDIA_CANDIDATES) {
            try {
                candidates.add(webLocation(page.resolve(matches.group()).toString()));
            } catch (IllegalArgumentException ignored) {
                // Keep inspecting the remaining resource candidates.
            }
        }
        for (URI candidate : candidates) {
            if (stopped || destroyed) {
                return;
            }
            notifyRequest(candidate);
        }
    }

    private void notifyRequest(URI location) {
        client.shouldInterceptRequest(this, new ResourceRequest(Uri.parse(location.toString())));
    }

    private void requireActive() {
        if (destroyed) {
            throw new IllegalStateException("WebView is destroyed");
        }
    }

    private static URI webLocation(String value) {
        URI location = URI.create(Objects.requireNonNull(value, "value"));
        String scheme = location.getScheme();
        if (location.getHost() == null || scheme == null
                || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
            throw new IllegalArgumentException("WebView only supports HTTP(S) locations");
        }
        return location;
    }

    private static boolean mediaLocation(URI location) {
        String path = location.getPath().toLowerCase(Locale.ROOT);
        return path.endsWith(".m3u8") || path.endsWith(".mp4") || path.endsWith(".mpd");
    }

    private static boolean headersContain(Map<String, String> headers, String expected) {
        return headers.keySet().stream().anyMatch(name -> name.equalsIgnoreCase(expected));
    }

    private static void addHeader(HttpRequest.Builder request, String name, String value) {
        if (name != null && value != null) {
            try {
                request.header(name, value);
            } catch (IllegalArgumentException ignored) {
                // Browser-restricted headers are ignored just like an Android WebView would do.
            }
        }
    }

    private record ResourceRequest(Uri url) implements WebResourceRequest {
        @Override
        public Uri getUrl() {
            return url;
        }
    }
}
