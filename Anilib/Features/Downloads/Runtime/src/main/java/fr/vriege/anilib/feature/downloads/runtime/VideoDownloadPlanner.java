package fr.vriege.anilib.feature.downloads.runtime;

import fr.vriege.anilib.feature.downloads.DownloadException;
import fr.vriege.anilib.feature.source.SourceContentUnitId;
import fr.vriege.anilib.feature.source.SourcePageResource;
import fr.vriege.anilib.feature.source.SourceStreamFormat;
import fr.vriege.anilib.feature.source.SourceVideoStream;
import fr.vriege.anilib.framework.http.AnilibHttpClient;
import fr.vriege.anilib.framework.http.HttpMethod;
import fr.vriege.anilib.framework.http.HttpRequest;
import fr.vriege.anilib.framework.http.HttpResponse;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class VideoDownloadPlanner {
    private static final int MAXIMUM_REDIRECTS = 5;
    private static final int MAXIMUM_PLAYLIST_DEPTH = 3;
    private static final int MAXIMUM_RESOURCES = 100_000;
    private static final int MAXIMUM_PLAYLIST_BYTES = 2 * 1024 * 1024;
    private static final long PROGRESSIVE_CHUNK_BYTES = 8L * 1024L * 1024L;
    private static final Pattern URI_ATTRIBUTE = Pattern.compile("URI=\\\"([^\\\"]+)\\\"");
    private static final Pattern BANDWIDTH = Pattern.compile("(?:^|,)BANDWIDTH=(\\d+)(?:,|$)");
    private static final Pattern CONTENT_RANGE_TOTAL = Pattern.compile(
            "bytes\\s+\\d+-\\d+/(\\d+)",
            Pattern.CASE_INSENSITIVE);

    private final AnilibHttpClient client;

    VideoDownloadPlanner(AnilibHttpClient client) {
        this.client = Objects.requireNonNull(client, "client");
    }

    VideoDownloadPlan plan(SourceContentUnitId unitId, SourceVideoStream stream) {
        SourceStreamFormat format = effectiveFormat(stream);
        return switch (format) {
            case HLS -> hls(unitId, stream, stream.location(), 0);
            case PROGRESSIVE -> progressive(unitId, stream);
            case DASH -> throw new DownloadException("DASH episode downloads are not supported yet");
            case AUTOMATIC -> throw new DownloadException("The video stream format could not be determined");
        };
    }

    byte[] fetch(VideoDownloadMetadata metadata, VideoDownloadResource resource) {
        HttpResponse response = exchange(
                HttpMethod.GET,
                resource.location(),
                metadata.headers(),
                resource.ranged() ? Optional.of(resource.rangeHeader()) : Optional.empty(),
                0);
        byte[] body = response.body();
        if (resource.ranged() && response.statusCode() != 206) {
            if (response.statusCode() != 200 || resource.rangeStart() != 0L
                    || body.length != resource.rangeEnd() + 1L) {
                throw new DownloadException("The video server refused resumable byte ranges");
            }
        } else if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new DownloadException("Video resource request failed with HTTP " + response.statusCode());
        }
        if (body.length == 0) {
            throw new DownloadException("Video resource response was empty");
        }
        if (resource.ranged() && body.length != resource.rangeEnd() - resource.rangeStart() + 1L) {
            throw new DownloadException("Video resource byte range was incomplete");
        }
        return body;
    }

    private VideoDownloadPlan progressive(SourceContentUnitId unitId, SourceVideoStream stream) {
        long length = contentLength(stream.location(), stream.headers());
        if (length < 1L) {
            throw new DownloadException("The video server did not expose a downloadable file size");
        }
        List<SourcePageResource> resources = new ArrayList<>();
        for (long start = 0L; start < length; start += PROGRESSIVE_CHUNK_BYTES) {
            if (resources.size() >= MAXIMUM_RESOURCES) {
                throw new DownloadException("Video contains too many download chunks");
            }
            long end = Math.min(length - 1L, start + PROGRESSIVE_CHUNK_BYTES - 1L);
            VideoDownloadResource resource = VideoDownloadResource.range(stream.location(), start, end);
            resources.add(new SourcePageResource(unitId, resource.encode(), resources.size(), end - start + 1L));
        }
        return new VideoDownloadPlan(
                new VideoDownloadMetadata(SourceStreamFormat.PROGRESSIVE, stream.headers(), ""),
                resources);
    }

    private VideoDownloadPlan hls(
            SourceContentUnitId unitId,
            SourceVideoStream stream,
            URI playlistLocation,
            int depth) {
        if (depth > MAXIMUM_PLAYLIST_DEPTH) {
            throw new DownloadException("HLS playlist nesting exceeds the supported limit");
        }
        HttpResponse response = exchange(HttpMethod.GET, playlistLocation, stream.headers(), Optional.empty(), 0);
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new DownloadException("HLS playlist request failed with HTTP " + response.statusCode());
        }
        byte[] body = response.body();
        if (body.length > MAXIMUM_PLAYLIST_BYTES) {
            throw new DownloadException("HLS playlist exceeds the supported size limit");
        }
        String playlist = new String(body, StandardCharsets.UTF_8);
        if (!playlist.stripLeading().startsWith("#EXTM3U")) {
            throw new DownloadException("The selected HLS stream returned an invalid playlist");
        }
        Optional<URI> variant = selectVariant(playlistLocation, playlist);
        if (variant.isPresent()) {
            return hls(unitId, stream, variant.orElseThrow(), depth + 1);
        }
        return mediaPlaylist(unitId, stream, playlistLocation, playlist);
    }

    private VideoDownloadPlan mediaPlaylist(
            SourceContentUnitId unitId,
            SourceVideoStream stream,
            URI playlistLocation,
            String playlist) {
        String[] lines = playlist.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
        List<SourcePageResource> resources = new ArrayList<>();
        StringBuilder local = new StringBuilder();
        for (String line : lines) {
            String stripped = line.strip();
            if (stripped.startsWith("#EXT-X-KEY:")
                    || stripped.startsWith("#EXT-X-MAP:")
                    || stripped.startsWith("#EXT-X-MEDIA:")) {
                local.append(rewriteUriAttribute(unitId, playlistLocation, line, resources)).append('\n');
            } else if (!stripped.isEmpty() && !stripped.startsWith("#")) {
                local.append(addResource(unitId, playlistLocation.resolve(stripped), resources)).append('\n');
            } else {
                local.append(line).append('\n');
            }
        }
        if (resources.isEmpty()) {
            throw new DownloadException("HLS playlist contains no downloadable media");
        }
        return new VideoDownloadPlan(
                new VideoDownloadMetadata(SourceStreamFormat.HLS, stream.headers(), local.toString()),
                resources);
    }

    private String rewriteUriAttribute(
            SourceContentUnitId unitId,
            URI playlistLocation,
            String line,
            List<SourcePageResource> resources) {
        Matcher matcher = URI_ATTRIBUTE.matcher(line);
        if (!matcher.find()) {
            return line;
        }
        String localName = addResource(unitId, playlistLocation.resolve(matcher.group(1)), resources);
        return matcher.replaceFirst(Matcher.quoteReplacement("URI=\"" + localName + "\""));
    }

    private String addResource(
            SourceContentUnitId unitId,
            URI location,
            List<SourcePageResource> resources) {
        if (resources.size() >= MAXIMUM_RESOURCES) {
            throw new DownloadException("HLS playlist contains too many resources");
        }
        int index = resources.size();
        resources.add(new SourcePageResource(
                unitId,
                VideoDownloadResource.complete(location).encode(),
                index,
                SourcePageResource.UNKNOWN_SIZE));
        return index + ".page";
    }

    private long contentLength(URI location, Map<String, String> headers) {
        HttpResponse head = exchange(HttpMethod.HEAD, location, headers, Optional.empty(), 0);
        if (head.statusCode() >= 200 && head.statusCode() < 300) {
            Optional<Long> length = positiveLong(head.firstHeader("content-length"));
            if (length.isPresent()) {
                return length.orElseThrow();
            }
        }
        HttpResponse ranged = exchange(HttpMethod.GET, location, headers, Optional.of("bytes=0-0"), 0);
        if (ranged.statusCode() == 206) {
            Matcher matcher = CONTENT_RANGE_TOTAL.matcher(ranged.firstHeader("content-range").orElse(""));
            if (matcher.matches()) {
                return Long.parseLong(matcher.group(1));
            }
        }
        return -1L;
    }

    private HttpResponse exchange(
            HttpMethod method,
            URI location,
            Map<String, String> headers,
            Optional<String> range,
            int redirects) {
        if (redirects > MAXIMUM_REDIRECTS) {
            throw new DownloadException("Video download exceeded the redirect limit");
        }
        HttpRequest.Builder request = HttpRequest.builder(location)
                .method(method)
                .timeout(Duration.ofSeconds(90));
        headers.forEach(request::header);
        range.ifPresent(value -> request.header("Range", value));
        HttpResponse response = client.execute(request.build());
        if (response.statusCode() >= 300 && response.statusCode() < 400) {
            String redirect = response.firstHeader("location")
                    .orElseThrow(() -> new DownloadException("Video redirect did not include a location"));
            return exchange(method, location.resolve(redirect), headers, range, redirects + 1);
        }
        return response;
    }

    private static SourceStreamFormat effectiveFormat(SourceVideoStream stream) {
        if (stream.format() != SourceStreamFormat.AUTOMATIC) {
            return stream.format();
        }
        String path = stream.location().getPath().toLowerCase(Locale.ROOT);
        if (path.endsWith(".m3u8")) {
            return SourceStreamFormat.HLS;
        }
        if (path.endsWith(".mpd")) {
            return SourceStreamFormat.DASH;
        }
        return SourceStreamFormat.PROGRESSIVE;
    }

    private static Optional<URI> selectVariant(URI location, String playlist) {
        String[] lines = playlist.replace("\r\n", "\n").replace('\r', '\n').split("\n");
        URI selected = null;
        long selectedBandwidth = Long.MIN_VALUE;
        for (int index = 0; index < lines.length - 1; index++) {
            String line = lines[index].strip();
            if (!line.startsWith("#EXT-X-STREAM-INF:")) {
                continue;
            }
            String candidate = lines[index + 1].strip();
            if (candidate.isEmpty() || candidate.startsWith("#")) {
                continue;
            }
            Matcher matcher = BANDWIDTH.matcher(line.substring("#EXT-X-STREAM-INF:".length()));
            long bandwidth = matcher.find() ? Long.parseLong(matcher.group(1)) : 0L;
            if (selected == null || bandwidth > selectedBandwidth) {
                selected = location.resolve(candidate);
                selectedBandwidth = bandwidth;
            }
        }
        return Optional.ofNullable(selected);
    }

    private static Optional<Long> positiveLong(Optional<String> value) {
        try {
            long parsed = Long.parseLong(value.orElse(""));
            return parsed > 0L ? Optional.of(parsed) : Optional.empty();
        } catch (NumberFormatException ignored) {
            return Optional.empty();
        }
    }
}
