package fr.vriege.anilib.feature.extensionrepository.ui;

import fr.vriege.anilib.feature.extensionrepository.ExtensionPackageMetadata;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface ApkExtensionPlatform {
    boolean available();

    default List<InstalledApkExtension> discoverInstalled() {
        return List.of();
    }

    default ApkExtensionRuntimeReport runtimeReport(InstalledApkExtension extensionPackage) {
        return ApkExtensionRuntimeReport.unsupported(extensionPackage.packageName());
    }

    default ApkExtensionRuntimeReport trustCertificate(
            InstalledApkExtension extensionPackage,
            String certificateSha256) {
        throw new UnsupportedOperationException("APK extension trust is unavailable on this platform");
    }

    default ApkExtensionRuntimeReport forgetCertificateTrust(InstalledApkExtension extensionPackage) {
        throw new UnsupportedOperationException("APK extension trust is unavailable on this platform");
    }

    CompletableFuture<String> install(ExtensionPackageMetadata extensionPackage);
}
