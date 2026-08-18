package fr.vriege.anilib.feature.extensionrepository.ui;

import fr.vriege.anilib.foundation.validation.Preconditions;

import java.net.URI;
import java.time.Instant;
import java.util.Optional;

public record ExtensionRepositoryRow(
        URI indexUri,
        Optional<Instant> fetchedAt,
        int packageCount,
        Optional<String> failure) {
    public ExtensionRepositoryRow {
        indexUri = Preconditions.requireNonNull(indexUri, "indexUri");
        fetchedAt = Preconditions.requireNonNull(fetchedAt, "fetchedAt");
        if (packageCount < 0) {
            throw new IllegalArgumentException("packageCount must not be negative");
        }
        failure = Preconditions.requireNonNull(failure, "failure");
    }
}
