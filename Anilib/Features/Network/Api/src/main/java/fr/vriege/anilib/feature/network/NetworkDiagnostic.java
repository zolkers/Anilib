package fr.vriege.anilib.feature.network;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

public record NetworkDiagnostic(
        String sourceId,
        URI endpoint,
        Instant checkedAt,
        Duration elapsed,
        int statusCode,
        boolean fromCache,
        boolean successful,
        String message) {
    public NetworkDiagnostic {
        sourceId = Objects.requireNonNull(sourceId, "sourceId must not be null").strip();
        if (sourceId.isEmpty() || sourceId.length() > 128) {
            throw new IllegalArgumentException("sourceId must contain 1 to 128 characters");
        }
        endpoint = Objects.requireNonNull(endpoint, "endpoint must not be null").normalize();
        checkedAt = Objects.requireNonNull(checkedAt, "checkedAt must not be null");
        elapsed = Objects.requireNonNull(elapsed, "elapsed must not be null");
        message = Objects.requireNonNull(message, "message must not be null").strip();
        if (elapsed.isNegative() || statusCode < 0 || statusCode > 999 || message.length() > 512) {
            throw new IllegalArgumentException("diagnostic values are outside their safe bounds");
        }
    }
}
