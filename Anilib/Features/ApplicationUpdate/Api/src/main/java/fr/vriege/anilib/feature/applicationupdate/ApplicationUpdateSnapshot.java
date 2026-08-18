package fr.vriege.anilib.feature.applicationupdate;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public record ApplicationUpdateSnapshot(
        ApplicationVersion currentVersion,
        ApplicationPlatform platform,
        ApplicationUpdateChannel channel,
        Optional<ApplicationRelease> availableRelease,
        Optional<Instant> lastCheckedAt,
        Optional<String> error) {
    public ApplicationUpdateSnapshot {
        Objects.requireNonNull(currentVersion, "currentVersion must not be null");
        Objects.requireNonNull(platform, "platform must not be null");
        Objects.requireNonNull(channel, "channel must not be null");
        availableRelease = Objects.requireNonNull(availableRelease, "availableRelease must not be null");
        lastCheckedAt = Objects.requireNonNull(lastCheckedAt, "lastCheckedAt must not be null");
        error = Objects.requireNonNull(error, "error must not be null")
                .map(String::strip)
                .filter(value -> !value.isEmpty());
    }

    public ApplicationUpdateSnapshot(
            ApplicationVersion currentVersion,
            ApplicationPlatform platform,
            Optional<ApplicationRelease> availableRelease,
            Optional<Instant> lastCheckedAt,
            Optional<String> error) {
        this(
                currentVersion,
                platform,
                ApplicationUpdateChannel.STABLE,
                availableRelease,
                lastCheckedAt,
                error);
    }
}
