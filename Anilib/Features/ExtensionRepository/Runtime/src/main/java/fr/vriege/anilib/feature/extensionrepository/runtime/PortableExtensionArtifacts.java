package fr.vriege.anilib.feature.extensionrepository.runtime;

import fr.vriege.anilib.feature.extensionrepository.InstalledExtensionPackage;

import java.nio.file.Path;

/** Canonical paths for artifacts owned by the extension installation directory. */
final class PortableExtensionArtifacts {
    private PortableExtensionArtifacts() {
    }

    static Path path(Path installationDirectory, InstalledExtensionPackage extension) {
        Path artifactDirectory = installationDirectory.toAbsolutePath().normalize().resolve("artifacts");
        String fileName = extension.packageName() + "-" + extension.versionCode()
                + "-" + extension.sha256().substring(0, 16) + ".jar";
        Path resolved = artifactDirectory.resolve(fileName).normalize();
        if (!resolved.getParent().equals(artifactDirectory)) {
            throw new IllegalStateException("Extension artifact path escapes its managed directory");
        }
        return resolved;
    }
}
