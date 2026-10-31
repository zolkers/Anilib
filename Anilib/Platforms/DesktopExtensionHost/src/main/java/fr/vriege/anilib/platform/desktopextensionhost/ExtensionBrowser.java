package fr.vriege.anilib.platform.desktopextensionhost;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/* Explicit platform adapter used by extension WebViews; no browser discovery or fake script execution. */
public interface ExtensionBrowser extends AutoCloseable {
    void load(String url, Map<String, String> headers);

    CompletableFuture<String> evaluate(String script);

    void stop();

    @Override
    void close();

    @FunctionalInterface
    interface Factory {
        ExtensionBrowser create(Consumer<String> resource, Consumer<String> pageFinished);
    }
}
