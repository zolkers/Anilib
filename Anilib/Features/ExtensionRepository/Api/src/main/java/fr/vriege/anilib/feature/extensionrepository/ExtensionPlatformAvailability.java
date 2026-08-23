package fr.vriege.anilib.feature.extensionrepository;

import fr.vriege.anilib.foundation.validation.Preconditions;

import java.util.Optional;

public record ExtensionPlatformAvailability(
        boolean desktop,
        Optional<ExtensionArtifactMetadata> portableArtifact,
        Optional<ExtensionArtifactMetadata> apkArtifact,
        Optional<ExtensionArtifactMetadata> preferredArtifact) {
    public ExtensionPlatformAvailability {
        portableArtifact = Preconditions.requireNonNull(portableArtifact, "portableArtifact");
        apkArtifact = Preconditions.requireNonNull(apkArtifact, "apkArtifact");
        preferredArtifact = Preconditions.requireNonNull(preferredArtifact, "preferredArtifact");
        if (desktop != preferredArtifact.isPresent()) {
            throw new IllegalArgumentException("desktop availability must match the preferred artifact");
        }
    }

    public static ExtensionPlatformAvailability from(ExtensionPackageMetadata extensionPackage) {
        ExtensionPackageMetadata metadata = Preconditions.requireNonNull(extensionPackage, "extensionPackage");
        Optional<ExtensionArtifactMetadata> portable = artifact(metadata, ExtensionArtifactFormat.ANILIB_BUNDLE);
        Optional<ExtensionArtifactMetadata> apk = artifact(metadata, ExtensionArtifactFormat.ANIYOMI_APK);
        Optional<ExtensionArtifactMetadata> preferred = portable.isPresent() ? portable : apk;
        return new ExtensionPlatformAvailability(
                preferred.isPresent(),
                portable,
                apk,
                preferred);
    }

    private static Optional<ExtensionArtifactMetadata> artifact(
            ExtensionPackageMetadata metadata,
            ExtensionArtifactFormat format) {
        return metadata.artifacts().stream()
                .filter(artifact -> artifact.format() == format)
                .findFirst();
    }
}
