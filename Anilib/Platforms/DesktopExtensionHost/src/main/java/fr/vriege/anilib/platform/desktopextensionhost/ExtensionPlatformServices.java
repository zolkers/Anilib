package fr.vriege.anilib.platform.desktopextensionhost;

import java.util.Objects;

/* Platform-owned services injected explicitly into the embedded host. */
public final class ExtensionPlatformServices {
    private static volatile ExtensionBrowser.Factory browserFactory;

    private ExtensionPlatformServices() {}

    public static void installBrowser(ExtensionBrowser.Factory factory) {
        browserFactory = Objects.requireNonNull(factory, "factory");
    }

    public static ExtensionBrowser.Factory browserFactory() {
        return browserFactory;
    }
}
