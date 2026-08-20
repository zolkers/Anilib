package fr.vriege.anilib.feature.extensionrepository.ui;

import fr.vriege.anilib.feature.extensionrepository.ExtensionPackageMetadata;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public interface ApkExtensionPlatform {
    boolean available();

    default boolean installationSupported() {
        return available();
    }

    default String availabilityDescription() {
        return available()
                ? "APK extensions can be installed on Android. Portable Anilib Bundles work on every platform."
                : "This device installs portable Anilib Bundles. APK execution is not configured.";
    }

    default String installActionLabel() {
        return "Install on Android";
    }

    default String installProgressLabel() {
        return "Handing APK to Android";
    }

    default boolean uninstallationSupported() {
        return false;
    }

    default String uninstallActionLabel() {
        return "Uninstall";
    }

    default List<InstalledApkExtension> discoverInstalled() {
        return List.of();
    }

    default Set<String> installedPackageNames() {
        return discoverInstalled().stream()
                .map(InstalledApkExtension::packageName)
                .collect(Collectors.toUnmodifiableSet());
    }

    default Set<String> activePackageNames() {
        return installedPackageNames();
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

    default CompletableFuture<String> uninstall(String packageName) {
        return CompletableFuture.failedFuture(
                new UnsupportedOperationException("APK extension removal is unavailable on this platform"));
    }

    CompletableFuture<String> install(ExtensionPackageMetadata extensionPackage);
}
