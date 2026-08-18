package fr.vriege.anilib.feature.extensionrepository.ui;

import fr.vriege.anilib.feature.extensionrepository.ExtensionPackageMetadata;

import java.util.concurrent.CompletableFuture;

/** Platform-neutral user actions for bring-your-own extension repositories. */
public interface ExtensionRepositoryPresentation {
    ExtensionRepositoryView snapshot();

    void add(String indexUrl);

    boolean remove(String indexUrl);

    CompletableFuture<ExtensionRepositoryView> refreshAll();

    CompletableFuture<ExtensionRepositoryView> updateAllAvailable();

    void setAutomaticUpdatesEnabled(boolean enabled);

    void trustKey(String keyId, String x509PublicKeyBase64);

    boolean forgetTrust(String keyId);

    CompletableFuture<ExtensionRepositoryView> install(ExtensionPackageMetadata extensionPackage);

    CompletableFuture<ExtensionRepositoryView> update(ExtensionPackageMetadata extensionPackage);

    void setEnabled(String packageName, boolean enabled);

    boolean removeInstalled(String packageName);

    AutoCloseable observe(Runnable listener);
}
