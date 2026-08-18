package fr.vriege.anilib.feature.extensionrepository.ui;

import fr.vriege.anilib.feature.extensionrepository.ExtensionPackageMetadata;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/** Platform integration for APK artifacts and installed APK extensions. */
public interface ApkExtensionPlatform {
    boolean available();

    /** Android-visible installed APK metadata; other platforms return an empty list. */
    default List<InstalledApkExtension> discoverInstalled() {
        return List.of();
    }

    /** Reports trust and host-ABI readiness without loading extension bytecode. */
    default ApkExtensionRuntimeReport runtimeReport(InstalledApkExtension extensionPackage) {
        return ApkExtensionRuntimeReport.unsupported(extensionPackage.packageName());
    }

    /** Records explicit trust for one of the package's current signing certificates. */
    default ApkExtensionRuntimeReport trustCertificate(
            InstalledApkExtension extensionPackage,
            String certificateSha256) {
        throw new UnsupportedOperationException("APK extension trust is unavailable on this platform");
    }

    /** Forgets the package-specific certificate trust decision. */
    default ApkExtensionRuntimeReport forgetCertificateTrust(InstalledApkExtension extensionPackage) {
        throw new UnsupportedOperationException("APK extension trust is unavailable on this platform");
    }

    CompletableFuture<String> install(ExtensionPackageMetadata extensionPackage);
}
