package fr.vriege.anilib.feature.extensionrepository;

import fr.vriege.anilib.foundation.validation.Preconditions;

import java.util.Optional;

public record ExtensionPlatformAvailability(
        boolean android,
        boolean desktop,
        Optional<ExtensionArtifactMetadata> androidArtifact,
        Optional<ExtensionArtifactMetadata> desktopArtifact) {
    public ExtensionPlatformAvailability {
        androidArtifact = Preconditions.requireNonNull(androidArtifact, "androidArtifact");
        desktopArtifact = Preconditions.requireNonNull(desktopArtifact, "desktopArtifact");
        if (android != androidArtifact.isPresent() || desktop != desktopArtifact.isPresent()) {
            throw new IllegalArgumentException("platform availability must match selected artifacts");
        }
    }

    public static ExtensionPlatformAvailability from(ExtensionPackageMetadata extensionPackage) {
        ExtensionPackageMetadata metadata = Preconditions.requireNonNull(extensionPackage, "extensionPackage");
        Optional<ExtensionArtifactMetadata> portable = artifact(metadata, ExtensionArtifactFormat.ANILIB_BUNDLE);
        Optional<ExtensionArtifactMetadata> apk = artifact(metadata, ExtensionArtifactFormat.ANIYOMI_APK);
        Optional<ExtensionArtifactMetadata> android = portable.isPresent() ? portable : apk;
        return new ExtensionPlatformAvailability(
                android.isPresent(),
                portable.isPresent(),
                android,
                portable);
    }

    public Optional<ExtensionArtifactMetadata> preferredArtifact(ExtensionHostPlatform platform) {
        return switch (Preconditions.requireNonNull(platform, "platform")) {
            case ANDROID -> androidArtifact;
            case DESKTOP -> desktopArtifact;
        };
    }

    private static Optional<ExtensionArtifactMetadata> artifact(
            ExtensionPackageMetadata metadata,
            ExtensionArtifactFormat format) {
        return metadata.artifacts().stream()
                .filter(artifact -> artifact.format() == format)
                .findFirst();
    }
}
