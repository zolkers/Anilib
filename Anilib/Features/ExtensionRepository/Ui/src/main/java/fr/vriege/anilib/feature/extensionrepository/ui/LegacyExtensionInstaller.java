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

    CompletableFuture<String> install(ExtensionPackageMetadata extensionPackage);
}
