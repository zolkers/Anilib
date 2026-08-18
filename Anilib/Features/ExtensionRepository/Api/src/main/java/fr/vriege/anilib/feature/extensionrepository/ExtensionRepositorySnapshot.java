package fr.vriege.anilib.feature.extensionrepository;

import fr.vriege.anilib.foundation.validation.Preconditions;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public record ExtensionRepositorySnapshot(
        URI indexUri,
        Instant fetchedAt,
        List<ExtensionPackageMetadata> packages,
        Optional<String> failure) {
    public ExtensionRepositorySnapshot {
        indexUri = Preconditions.requireNonNull(indexUri, "indexUri");
        fetchedAt = Preconditions.requireNonNull(fetchedAt, "fetchedAt");
        packages = List.copyOf(Preconditions.requireNonNull(packages, "packages"));
        failure = Preconditions.requireNonNull(failure, "failure")
                .map(value -> Preconditions.requireNonBlank(value, "failure"));
        if (failure.isPresent() && !packages.isEmpty()) {
            throw new IllegalArgumentException("failed repository snapshot cannot expose packages");
        }
    }

    public boolean successful() {
        return failure.isEmpty();
    }
}
