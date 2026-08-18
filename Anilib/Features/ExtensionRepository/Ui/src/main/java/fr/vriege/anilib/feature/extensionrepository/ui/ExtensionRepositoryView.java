package fr.vriege.anilib.feature.extensionrepository.ui;

import fr.vriege.anilib.feature.extensionrepository.ExtensionPackageMetadata;
import fr.vriege.anilib.feature.extensionrepository.InstalledExtensionPackage;
import fr.vriege.anilib.feature.extensionrepository.ExtensionUpdateCandidate;
import fr.vriege.anilib.foundation.validation.Preconditions;

import java.util.List;
import java.util.Set;

public record ExtensionRepositoryView(
        List<ExtensionRepositoryRow> repositories,
        List<ExtensionPackageMetadata> packages,
        List<InstalledExtensionPackage> installed,
        List<ExtensionUpdateCandidate> updates,
        boolean automaticUpdatesEnabled,
        List<String> availableLanguages,
        Set<String> enabledLanguages,
        Set<String> pinnedPackages,
        List<String> trustedKeyIds) {
    public ExtensionRepositoryView {
        repositories = List.copyOf(Preconditions.requireNonNull(repositories, "repositories"));
        packages = List.copyOf(Preconditions.requireNonNull(packages, "packages"));
        installed = List.copyOf(Preconditions.requireNonNull(installed, "installed"));
        updates = List.copyOf(Preconditions.requireNonNull(updates, "updates"));
        availableLanguages = List.copyOf(Preconditions.requireNonNull(availableLanguages, "availableLanguages"));
        enabledLanguages = Set.copyOf(Preconditions.requireNonNull(enabledLanguages, "enabledLanguages"));
        pinnedPackages = Set.copyOf(Preconditions.requireNonNull(pinnedPackages, "pinnedPackages"));
        trustedKeyIds = List.copyOf(Preconditions.requireNonNull(trustedKeyIds, "trustedKeyIds"));
    }
}
