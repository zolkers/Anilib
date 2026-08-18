package fr.vriege.anilib.feature.localsource;

import java.time.Instant;
import java.util.Objects;

public record LocalSourceScan(
        long revision,
        Instant scannedAt,
        int mangaSeries,
        int animeSeries,
        int chapters,
        int episodes) {
    public LocalSourceScan {
        if (revision < 1) {
            throw new IllegalArgumentException("revision must be positive");
        }
        if (mangaSeries < 0 || animeSeries < 0 || chapters < 0 || episodes < 0) {
            throw new IllegalArgumentException("local source scan counts must be non-negative");
        }
        Objects.requireNonNull(scannedAt, "scannedAt must not be null");
    }
}
