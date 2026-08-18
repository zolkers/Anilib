package fr.vriege.anilib.feature.extensionrepository;

import fr.vriege.anilib.foundation.validation.Preconditions;

import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

public record InstalledExtensionPackage(
        String packageName,
        String displayName,
        long versionCode,
        String versionName,
        ExtensionArtifactFormat format,
        ExtensionInstallationState state,
        String sha256,
        Optional<String> signingKeyId,
        Instant installedAt) {
    private static final Pattern SHA_256 = Pattern.compile("[0-9a-f]{64}");

    public InstalledExtensionPackage {
        packageName = ExtensionPackageIdentifiers.requireValid(packageName);
        displayName = Preconditions.requireNonBlank(displayName, "displayName");
        if (versionCode < 0) {
            throw new IllegalArgumentException("versionCode must not be negative");
        }
        versionName = Preconditions.requireNonBlank(versionName, "versionName");
        format = Preconditions.requireNonNull(format, "format");
        state = Preconditions.requireNonNull(state, "state");
        sha256 = Preconditions.requireNonBlank(sha256, "sha256").toLowerCase(Locale.ROOT);
        if (!SHA_256.matcher(sha256).matches()) {
            throw new IllegalArgumentException("sha256 must contain 64 hexadecimal characters");
        }
        signingKeyId = Preconditions.requireNonNull(signingKeyId, "signingKeyId")
                .map(value -> Preconditions.requireNonBlank(value, "signingKeyId"));
        installedAt = Preconditions.requireNonNull(installedAt, "installedAt");
    }
}
