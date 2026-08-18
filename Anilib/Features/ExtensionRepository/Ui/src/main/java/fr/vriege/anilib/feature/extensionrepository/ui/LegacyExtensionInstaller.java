package fr.vriege.anilib.feature.extensionrepository.ui;

import fr.vriege.anilib.feature.extensionrepository.ExtensionPackageMetadata;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/** Optional platform hand-off for user-selected legacy Aniyomi APK artifacts. */
public interface LegacyExtensionInstaller {
    boolean available();

    /** Android-visible installed APK metadata; other platforms return an empty list. */
    default List<LegacyExtensionPackage> discoverInstalled() {
        return List.of();
    }

    /** Reports trust and host-ABI readiness without loading extension bytecode. */
    default LegacyExtensionRuntimeReport runtimeReport(LegacyExtensionPackage extensionPackage) {
        return LegacyExtensionRuntimeReport.unsupported(extensionPackage.packageName());
    }

    /** Records explicit trust for one of the package's current signing certificates. */
    default LegacyExtensionRuntimeReport trustCertificate(
            LegacyExtensionPackage extensionPackage,
            String certificateSha256) {
        throw new UnsupportedOperationException("Legacy extension trust is unavailable on this platform");
    }

    /** Forgets the package-specific certificate trust decision. */
    default LegacyExtensionRuntimeReport forgetCertificateTrust(LegacyExtensionPackage extensionPackage) {
        throw new UnsupportedOperationException("Legacy extension trust is unavailable on this platform");
    }

    CompletableFuture<String> install(ExtensionPackageMetadata extensionPackage);
}
