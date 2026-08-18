package fr.vriege.anilib.feature.extensionrepository.runtime;

import fr.vriege.anilib.feature.extensionrepository.InstalledExtensionPackage;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

final class PortableExtensionArtifacts {
    private PortableExtensionArtifacts() {
    }

    static Path path(Path installationDirectory, InstalledExtensionPackage extension) {
        Path artifactDirectory = installationDirectory.toAbsolutePath().normalize().resolve("artifacts");
        String fileName = packageHash(extension.packageName()) + "-" + extension.versionCode()
                + "-" + extension.sha256().substring(0, 16) + ".jar";
        Path resolved = artifactDirectory.resolve(fileName).normalize();
        if (!resolved.getParent().equals(artifactDirectory)) {
            throw new IllegalStateException("Extension artifact path escapes its managed directory");
        }
        return resolved;
    }

    private static String packageHash(String packageName) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(packageName.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 16);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JDK does not provide SHA-256", exception);
        }
    }
}
