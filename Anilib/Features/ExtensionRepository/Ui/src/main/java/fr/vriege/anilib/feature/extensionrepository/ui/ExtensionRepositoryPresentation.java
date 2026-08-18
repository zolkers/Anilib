package fr.vriege.anilib.feature.extensionrepository.ui;

import java.util.concurrent.CompletableFuture;

/** Platform-neutral user actions for bring-your-own extension repositories. */
public interface ExtensionRepositoryPresentation {
    ExtensionRepositoryView snapshot();

    void add(String indexUrl);

    boolean remove(String indexUrl);

    CompletableFuture<ExtensionRepositoryView> refreshAll();

    AutoCloseable observe(Runnable listener);
}
