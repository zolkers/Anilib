package fr.vriege.anilib.feature.extensionrepository.ui;

import fr.vriege.anilib.feature.extensionrepository.ExtensionPackageMetadata;

import java.util.concurrent.CompletableFuture;

/** Dependency-free APK defaults for platforms without Android package support. */
public final class ApkExtensionPlatforms {
    private static final ApkExtensionPlatform UNAVAILABLE = new ApkExtensionPlatform() {
        @Override
        public boolean available() {
            return false;
        }

        @Override
        public CompletableFuture<String> install(ExtensionPackageMetadata extensionPackage) {
            return CompletableFuture.failedFuture(
                    new UnsupportedOperationException("APK extensions can only be installed on Android"));
        }
    };

    private ApkExtensionPlatforms() {
    }

    public static ApkExtensionPlatform unavailable() {
        return UNAVAILABLE;
    }
}
