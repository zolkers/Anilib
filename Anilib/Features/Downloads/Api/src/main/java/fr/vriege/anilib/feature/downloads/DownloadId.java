package fr.vriege.anilib.feature.downloads;

import java.util.Objects;
import java.util.UUID;

/** Stable identity for one durable download job. */
public record DownloadId(UUID value) implements Comparable<DownloadId> {
    public DownloadId {
        Objects.requireNonNull(value, "value must not be null");
    }

    public static DownloadId create() {
        return new DownloadId(UUID.randomUUID());
    }

    public static DownloadId parse(String value) {
        return new DownloadId(UUID.fromString(value));
    }

    @Override
    public int compareTo(DownloadId other) {
        return value.compareTo(other.value);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
