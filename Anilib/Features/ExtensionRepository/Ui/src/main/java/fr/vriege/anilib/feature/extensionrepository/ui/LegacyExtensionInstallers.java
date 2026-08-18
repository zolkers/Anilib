package fr.vriege.anilib.feature.extensionrepository.ui;

import fr.vriege.anilib.feature.extensionrepository.ExtensionPackageMetadata;

import java.util.concurrent.CompletableFuture;

/** Dependency-free legacy installer defaults for platforms without APK support. */
public final class LegacyExtensionInstallers {
    private static final LegacyExtensionInstaller UNAVAILABLE = new LegacyExtensionInstaller() {
        @Override
        public boolean available() {
            return false;
        }

        @Override
        public CompletableFuture<String> install(ExtensionPackageMetadata extensionPackage) {
            return CompletableFuture.failedFuture(
                    new UnsupportedOperationException("Aniyomi APKs can only be installed on Android"));
        }
    };

    private LegacyExtensionInstallers() {
    }

    public static LegacyExtensionInstaller unavailable() {
        return UNAVAILABLE;
    }
}
