package fr.vriege.anilib.feature.source;

import fr.vriege.anilib.foundation.validation.Preconditions;

import java.net.URI;
import java.util.Map;
import java.util.Optional;

public record SourceSubtitleTrack(
        String id,
        String label,
        Optional<String> language,
        URI location,
        Map<String, String> headers) {
    public SourceSubtitleTrack {
        id = Preconditions.requireNonBlank(id, "id");
        label = Preconditions.requireNonBlank(label, "label");
        language = Preconditions.requireNonNull(language, "language")
                .map(String::strip)
                .filter(value -> !value.isEmpty());
        location = absoluteLocation(location);
        headers = immutableHeaders(headers);
    }

    private static URI absoluteLocation(URI value) {
        URI location = Preconditions.requireNonNull(value, "location");
        if (!location.isAbsolute() || location.getScheme().isBlank()) {
            throw new IllegalArgumentException("location must be an absolute URI");
        }
        return location;
    }

    static Map<String, String> immutableHeaders(Map<String, String> values) {
        Preconditions.requireNonNull(values, "headers");
        values.forEach((name, value) -> {
            Preconditions.requireNonBlank(name, "header name");
            Preconditions.requireNonNull(value, "header value");
        });
        return Map.copyOf(values);
    }
}
