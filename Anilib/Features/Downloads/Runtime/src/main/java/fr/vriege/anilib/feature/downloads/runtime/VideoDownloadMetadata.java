package fr.vriege.anilib.feature.downloads.runtime;

import fr.vriege.anilib.feature.source.SourceStreamFormat;

import java.util.Map;

record VideoDownloadMetadata(
        SourceStreamFormat format,
        Map<String, String> headers,
        String offlinePlaylist) {
    VideoDownloadMetadata {
        if (format == null || format == SourceStreamFormat.AUTOMATIC || format == SourceStreamFormat.DASH) {
            throw new IllegalArgumentException("downloaded video format must be HLS or progressive");
        }
        headers = Map.copyOf(headers);
        offlinePlaylist = offlinePlaylist == null ? "" : offlinePlaylist;
        if (format == SourceStreamFormat.HLS && offlinePlaylist.isBlank()) {
            throw new IllegalArgumentException("HLS downloads require an offline playlist");
        }
        if (format == SourceStreamFormat.PROGRESSIVE && !offlinePlaylist.isEmpty()) {
            throw new IllegalArgumentException("progressive downloads cannot contain a playlist");
        }
    }

    boolean hls() {
        return format == SourceStreamFormat.HLS;
    }
}
