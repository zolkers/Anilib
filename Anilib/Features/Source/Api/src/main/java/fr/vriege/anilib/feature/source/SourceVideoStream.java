package fr.vriege.anilib.feature.source;

import fr.vriege.anilib.foundation.validation.Preconditions;

import java.net.URI;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public record SourceVideoStream(
        String id,
        String quality,
        URI location,
        SourceStreamFormat format,
        Map<String, String> headers,
        List<SourceSubtitleTrack> subtitles) {
    public SourceVideoStream {
        id = Preconditions.requireNonBlank(id, "id");
        quality = Preconditions.requireNonBlank(quality, "quality");
        location = Preconditions.requireNonNull(location, "location");
        if (!location.isAbsolute() || location.getScheme().isBlank()) {
            throw new IllegalArgumentException("location must be an absolute URI");
        }
        format = Preconditions.requireNonNull(format, "format");
        headers = SourceSubtitleTrack.immutableHeaders(headers);
        subtitles = List.copyOf(Preconditions.requireNonNull(subtitles, "subtitles"));
        Set<String> subtitleIds = new HashSet<>();
        for (SourceSubtitleTrack subtitle : subtitles) {
            Preconditions.requireNonNull(subtitle, "subtitle");
            if (!subtitleIds.add(subtitle.id())) {
                throw new IllegalArgumentException("subtitle ids must be unique within a stream");
            }
        }
    }
}
