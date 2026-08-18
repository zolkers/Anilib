package fr.vriege.anilib.feature.extensionrepository.ui;

import fr.vriege.anilib.feature.extensionrepository.ExtensionPackageMetadata;

import java.util.concurrent.CompletableFuture;

/** Optional platform hand-off for user-selected legacy Aniyomi APK artifacts. */
public interface LegacyExtensionInstaller {
    boolean available();

    CompletableFuture<String> install(ExtensionPackageMetadata extensionPackage);
}
