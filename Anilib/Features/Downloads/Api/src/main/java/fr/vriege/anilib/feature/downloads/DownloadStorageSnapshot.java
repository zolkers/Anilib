package fr.vriege.anilib.feature.downloads;

import java.nio.file.Path;
import java.util.Objects;

public record DownloadStorageSnapshot(
        Path location,
        boolean customLocation,
        boolean writable,
        long availableBytes) {
    public DownloadStorageSnapshot {
        location = Objects.requireNonNull(location, "location must not be null")
                .toAbsolutePath()
                .normalize();
        if (availableBytes < 0L) {
            throw new IllegalArgumentException("availableBytes must not be negative");
        }
    }
}
