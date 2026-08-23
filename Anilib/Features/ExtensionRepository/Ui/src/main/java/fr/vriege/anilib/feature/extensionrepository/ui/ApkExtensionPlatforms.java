package fr.vriege.anilib.feature.extensionrepository.ui;

import fr.vriege.anilib.feature.extensionrepository.ExtensionPackageMetadata;

import java.util.concurrent.CompletableFuture;

public final class ApkExtensionPlatforms {
    private static final ApkExtensionPlatform UNAVAILABLE = new ApkExtensionPlatform() {
        @Override
        public boolean available() {
            return false;
        }

        @Override
        public CompletableFuture<String> install(ExtensionPackageMetadata extensionPackage) {
            return CompletableFuture.failedFuture(
                    new UnsupportedOperationException("The Desktop APK extension host is unavailable"));
        }
    };

    private ApkExtensionPlatforms() {
    }

    public static ApkExtensionPlatform unavailable() {
        return UNAVAILABLE;
    }
}
