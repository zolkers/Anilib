package fr.vriege.anilib.feature.settings;

import java.util.Objects;

public record StorageUsage(String area, long bytes, long files) {
    public StorageUsage {
        area = Objects.requireNonNull(area, "area must not be null").strip();
        if (area.isEmpty() || bytes < 0 || files < 0) {
            throw new IllegalArgumentException("storage usage values are invalid");
        }
    }
}
