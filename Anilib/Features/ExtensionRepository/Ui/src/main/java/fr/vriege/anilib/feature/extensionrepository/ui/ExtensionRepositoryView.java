package fr.vriege.anilib.feature.extensionrepository.ui;

import fr.vriege.anilib.feature.extensionrepository.ExtensionPackageMetadata;
import fr.vriege.anilib.feature.extensionrepository.InstalledExtensionPackage;
import fr.vriege.anilib.feature.extensionrepository.ExtensionUpdateCandidate;
import fr.vriege.anilib.foundation.validation.Preconditions;

import java.util.List;

public record ExtensionRepositoryView(
        List<ExtensionRepositoryRow> repositories,
        List<ExtensionPackageMetadata> packages,
        List<InstalledExtensionPackage> installed,
        List<ExtensionUpdateCandidate> updates,
        boolean automaticUpdatesEnabled,
        List<String> trustedKeyIds) {
    public ExtensionRepositoryView {
        repositories = List.copyOf(Preconditions.requireNonNull(repositories, "repositories"));
        packages = List.copyOf(Preconditions.requireNonNull(packages, "packages"));
        installed = List.copyOf(Preconditions.requireNonNull(installed, "installed"));
        updates = List.copyOf(Preconditions.requireNonNull(updates, "updates"));
        trustedKeyIds = List.copyOf(Preconditions.requireNonNull(trustedKeyIds, "trustedKeyIds"));
    }
}
