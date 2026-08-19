package fr.vriege.anilib.platform.desktopextensionhost.extension;

import fr.vriege.anilib.platform.desktopextensionhost.compat.aniyomi.animesource.model.Video;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import okhttp3.Headers;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.jsoup.nodes.Entities;

final class EmbeddedVideoFallback {
    private static final int MAX_CANDIDATE_PAGES = 16;
    private static final int MAX_DOCUMENT_BYTES = 2 * 1024 * 1024;
    private static final Pattern PAGE_LOCATION = Pattern.compile(
            "https?://[^\\s\\\\\"']+", Pattern.CASE_INSENSITIVE);
    private static final Pattern MEDIA_LOCATION = Pattern.compile(
            "(?i)(?:src|file)\\s*[:=]\\s*['\"]([^'\"]+\\.(?:m3u8|mp4)(?:\\?[^'\"]*)?)['\"]");

    private EmbeddedVideoFallback() {
    }

    static List<Video> resolve(OkHttpClient client, Headers sourceHeaders, String episodeData) {
        var videos = new ArrayList<Video>();
        var media = new LinkedHashSet<URI>();
        int visited = 0;
        var pages = PAGE_LOCATION.matcher(episodeData);
        while (pages.find() && visited++ < MAX_CANDIDATE_PAGES) {
            URI page = webUri(pages.group());
            if (page == null) {
                continue;
            }
            if (mediaLocation(page.toString())) {
                addVideo(videos, media, page, page, sourceHeaders);
                continue;
            }
            try (Response response = client.newCall(new Request.Builder()
                    .url(page.toString())
                    .headers(sourceHeaders)
                    .build()).execute()) {
                if (!response.isSuccessful() || response.body() == null) {
                    continue;
                }
                byte[] document = response.body().byteStream().readNBytes(MAX_DOCUMENT_BYTES + 1);
                if (document.length > MAX_DOCUMENT_BYTES) {
                    continue;
                }
                for (URI location : mediaLocations(page, new String(document, StandardCharsets.UTF_8))) {
                    addVideo(videos, media, page, location, sourceHeaders);
                }
            } catch (IOException | IllegalArgumentException ignored) {
                // A failed embed must not prevent the remaining extension-provided players from resolving.
            }
            if (!videos.isEmpty()) {
                break;
            }
        }
        return List.copyOf(videos);
    }

    static List<URI> mediaLocations(URI page, String document) {
        var result = new LinkedHashSet<URI>();
        var matches = MEDIA_LOCATION.matcher(document);
        while (matches.find()) {
            String raw = Entities.unescape(matches.group(1)).replace("\\/", "/");
            try {
                URI resolved = webUri(page.resolve(raw).toString());
                if (resolved != null && mediaLocation(resolved.toString())) {
                    result.add(resolved);
                }
            } catch (IllegalArgumentException ignored) {
                // Ignore malformed media candidates and continue scanning the bounded document.
            }
        }
        return List.copyOf(result);
    }

    private static void addVideo(
            List<Video> videos,
            LinkedHashSet<URI> seen,
            URI page,
            URI media,
            Headers sourceHeaders) {
        if (!seen.add(media)) {
            return;
        }
        Headers.Builder headers = sourceHeaders.newBuilder().set("Referer", page.toString());
        headers.set("Origin", page.getScheme() + "://" + page.getRawAuthority());
        String host = page.getHost() == null ? "Embedded player" : page.getHost();
        videos.add(new Video(
                page.toString(), host + " (automatic)", media.toString(), headers.build(), List.of(), List.of()));
    }

    private static URI webUri(String value) {
        try {
            URI result = URI.create(value);
            String scheme = result.getScheme();
            if (result.getHost() == null || scheme == null
                    || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
                return null;
            }
            return result;
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static boolean mediaLocation(String value) {
        String path = URI.create(value).getPath().toLowerCase(Locale.ROOT);
        return path.endsWith(".m3u8") || path.endsWith(".mp4");
    }
}
