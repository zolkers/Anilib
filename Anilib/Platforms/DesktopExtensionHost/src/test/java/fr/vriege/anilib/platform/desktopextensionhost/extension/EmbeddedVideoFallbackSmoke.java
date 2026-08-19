package fr.vriege.anilib.platform.desktopextensionhost.extension;

import java.net.URI;
import java.util.List;

public final class EmbeddedVideoFallbackSmoke {
    private EmbeddedVideoFallbackSmoke() {
    }

    public static void verify() {
        List<URI> sibnet = EmbeddedVideoFallback.mediaLocations(
                URI.create("https://video.sibnet.ru/shell.php?videoid=1"),
                "player.src([{src: \"/v/token/1.mp4\", type: \"video/mp4\"}]);");
        if (!sibnet.equals(List.of(URI.create("https://video.sibnet.ru/v/token/1.mp4")))) {
            throw new IllegalStateException("relative MP4 embed fallback is not deterministic");
        }

        List<URI> hls = EmbeddedVideoFallback.mediaLocations(
                URI.create("https://embed.example/watch/one"),
                "sources: [{ file: 'https://cdn.example/master.m3u8?t=token&amp;s=1' }]");
        if (!hls.equals(List.of(URI.create("https://cdn.example/master.m3u8?t=token&s=1")))) {
            throw new IllegalStateException("absolute HLS embed fallback is not deterministic");
        }

        if (!EmbeddedVideoFallback.mediaLocations(
                URI.create("https://embed.example/watch/one"),
                "poster: 'https://cdn.example/poster.jpg'").isEmpty()) {
            throw new IllegalStateException("embed fallback must ignore non-media assets");
        }
    }
}
