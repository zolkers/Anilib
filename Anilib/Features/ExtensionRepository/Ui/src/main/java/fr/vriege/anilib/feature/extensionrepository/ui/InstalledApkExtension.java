package fr.vriege.anilib.feature.extensionrepository.ui;

import fr.vriege.anilib.foundation.validation.Preconditions;

import java.util.List;
import java.util.Optional;

/** Safe metadata projection of one Android-visible extension APK. */
public record InstalledApkExtension(
        String packageName,
        String displayName,
        long versionCode,
        String versionName,
        String libraryVersion,
        boolean adult,
        boolean torrent,
        List<String> sourceEntrypoints,
        Optional<String> sourceFactory,
        boolean hasReadme,
        boolean hasChangelog,
        List<String> signingCertificateSha256,
        ApkExtensionCompatibility compatibility) {
    public InstalledApkExtension {
        packageName = Preconditions.requireNonBlank(packageName, "packageName");
        displayName = Preconditions.requireNonBlank(displayName, "displayName");
        if (versionCode < 0) {
            throw new IllegalArgumentException("versionCode must not be negative");
        }
        versionName = Preconditions.requireNonBlank(versionName, "versionName");
        libraryVersion = Preconditions.requireNonBlank(libraryVersion, "libraryVersion");
        sourceEntrypoints = List.copyOf(Preconditions.requireNonNull(sourceEntrypoints, "sourceEntrypoints"));
        sourceFactory = Preconditions.requireNonNull(sourceFactory, "sourceFactory")
                .map(value -> Preconditions.requireNonBlank(value, "sourceFactory"));
        signingCertificateSha256 = List.copyOf(Preconditions.requireNonNull(
                signingCertificateSha256,
                "signingCertificateSha256"));
        compatibility = Preconditions.requireNonNull(compatibility, "compatibility");
    }
}
