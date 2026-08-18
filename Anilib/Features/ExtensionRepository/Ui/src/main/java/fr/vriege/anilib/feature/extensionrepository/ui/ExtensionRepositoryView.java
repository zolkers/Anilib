package fr.vriege.anilib.feature.extensionrepository.ui;

import fr.vriege.anilib.feature.extensionrepository.ExtensionPackageMetadata;
import fr.vriege.anilib.foundation.validation.Preconditions;

import java.util.List;

/** Immutable repository and package snapshot for Android and desktop Compose. */
public record ExtensionRepositoryView(
        List<ExtensionRepositoryRow> repositories,
        List<ExtensionPackageMetadata> packages) {
    public ExtensionRepositoryView {
        repositories = List.copyOf(Preconditions.requireNonNull(repositories, "repositories"));
        packages = List.copyOf(Preconditions.requireNonNull(packages, "packages"));
    }
}
