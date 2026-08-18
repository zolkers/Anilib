package fr.vriege.anilib.feature.extensionrepository;

import fr.vriege.anilib.foundation.validation.Preconditions;

/** A newer repository package matching one installed portable extension. */
public record ExtensionUpdateCandidate(
        InstalledExtensionPackage installed,
        ExtensionPackageMetadata available,
        boolean automaticEligible) {
    public ExtensionUpdateCandidate {
        installed = Preconditions.requireNonNull(installed, "installed");
        available = Preconditions.requireNonNull(available, "available");
        if (!installed.packageName().equals(available.packageName())) {
            throw new IllegalArgumentException("update package identity must match the installed extension");
        }
        if (available.versionCode() <= installed.versionCode()) {
            throw new IllegalArgumentException("update version must be newer than the installed extension");
        }
    }
}
