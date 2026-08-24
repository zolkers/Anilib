package fr.vriege.anilib.feature.downloads.runtime;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

record VideoDownloadResource(URI location, long rangeStart, long rangeEnd) {
    private static final String PREFIX = "video-v1:";

    VideoDownloadResource {
        if (location == null || !location.isAbsolute()) {
            throw new IllegalArgumentException("video resource location must be absolute");
        }
        if (rangeStart < -1L || rangeEnd < -1L || (rangeStart < 0L) != (rangeEnd < 0L)
                || rangeStart >= 0L && rangeEnd < rangeStart) {
            throw new IllegalArgumentException("video resource byte range is invalid");
        }
    }

    static VideoDownloadResource complete(URI location) {
        return new VideoDownloadResource(location, -1L, -1L);
    }

    static VideoDownloadResource range(URI location, long start, long end) {
        return new VideoDownloadResource(location, start, end);
    }

    boolean ranged() {
        return rangeStart >= 0L;
    }

    String rangeHeader() {
        if (!ranged()) {
            throw new IllegalStateException("complete resources do not have a byte range");
        }
        return "bytes=" + rangeStart + '-' + rangeEnd;
    }

    String encode() {
        String encodedLocation = Base64.getUrlEncoder().withoutPadding().encodeToString(
                location.toASCIIString().getBytes(StandardCharsets.UTF_8));
        return PREFIX + rangeStart + ':' + rangeEnd + ':' + encodedLocation;
    }

    static VideoDownloadResource decode(String value) {
        if (value == null || !value.startsWith(PREFIX)) {
            throw new IllegalArgumentException("video resource metadata is invalid");
        }
        String[] fields = value.substring(PREFIX.length()).split(":", 3);
        if (fields.length != 3) {
            throw new IllegalArgumentException("video resource metadata is incomplete");
        }
        String location = new String(Base64.getUrlDecoder().decode(fields[2]), StandardCharsets.UTF_8);
        return new VideoDownloadResource(
                URI.create(location),
                Long.parseLong(fields[0]),
                Long.parseLong(fields[1]));
    }
}
