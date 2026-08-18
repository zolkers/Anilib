package fr.vriege.anilib.feature.extensionrepository;

import fr.vriege.anilib.foundation.validation.Preconditions;

import java.time.Instant;
import java.util.Locale;
import java.util.regex.Pattern;

/** Durable, platform-neutral status of one verified extension artifact. */
public record InstalledExtensionPackage(
        String packageName,
        String displayName,
        long versionCode,
        String versionName,
        ExtensionArtifactFormat format,
        ExtensionInstallationState state,
        String sha256,
        Instant installedAt) {
    private static final Pattern SHA_256 = Pattern.compile("[0-9a-f]{64}");
    private static final Pattern PACKAGE_NAME = Pattern.compile("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+");

    public InstalledExtensionPackage {
        packageName = Preconditions.requireNonBlank(packageName, "packageName");
        if (!PACKAGE_NAME.matcher(packageName).matches()) {
            throw new IllegalArgumentException("packageName must use Java package syntax");
        }
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
        installedAt = Preconditions.requireNonNull(installedAt, "installedAt");
    }
}
