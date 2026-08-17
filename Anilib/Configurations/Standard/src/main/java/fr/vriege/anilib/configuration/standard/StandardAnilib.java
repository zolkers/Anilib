package fr.vriege.anilib.configuration.standard;

import fr.vriege.anilib.feature.library.bundle.LibraryPlugin;
import fr.vriege.anilib.feature.discovery.bundle.DiscoveryPlugin;
import fr.vriege.anilib.feature.localsource.bundle.LocalSourcePlugin;
import fr.vriege.anilib.feature.network.bundle.NetworkPlugin;
import fr.vriege.anilib.feature.reader.bundle.ReaderPlugin;
import fr.vriege.anilib.feature.source.bundle.SourceSdkPlugin;
import fr.vriege.anilib.framework.http.HttpTransport;
import fr.vriege.anilib.framework.http.runtime.UrlConnectionHttpTransport;
import fr.vriege.anilib.kernel.AnilibPlugin;
import fr.vriege.anilib.kernel.StartedAnilib;
import fr.vriege.anilib.kernel.runtime.DefaultPluginEngine;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/** Canonical product selection shared by supported platform applications. */
public final class StandardAnilib {
    private StandardAnilib() {
    }

    public static StartedAnilib start(Path dataDirectory) {
        return start(dataDirectory, List.of());
    }

    public static StartedAnilib start(
            Path dataDirectory,
            Collection<? extends AnilibPlugin> additionalPlugins) {
        return start(dataDirectory, new UrlConnectionHttpTransport(), additionalPlugins);
    }

    public static StartedAnilib start(
            Path dataDirectory,
            HttpTransport httpTransport,
            Collection<? extends AnilibPlugin> additionalPlugins) {
        Objects.requireNonNull(dataDirectory, "dataDirectory must not be null");
        Objects.requireNonNull(httpTransport, "httpTransport must not be null");
        Objects.requireNonNull(additionalPlugins, "additionalPlugins must not be null");
        Path libraryFile = dataDirectory.toAbsolutePath().normalize().resolve("library.anilib");
        Path localContent = dataDirectory.toAbsolutePath().normalize().resolve("local-content");
        Path httpCache = dataDirectory.toAbsolutePath().normalize().resolve("http-cache");
        Path sourcePreferences = dataDirectory.toAbsolutePath().normalize().resolve("source-preferences.properties");
        List<AnilibPlugin> plugins = new ArrayList<>();
        plugins.add(new LibraryPlugin(libraryFile));
        plugins.add(new SourceSdkPlugin());
        plugins.add(new LocalSourcePlugin(localContent));
        plugins.add(new NetworkPlugin(httpCache, httpTransport));
        plugins.add(new DiscoveryPlugin(sourcePreferences));
        plugins.add(new ReaderPlugin());
        plugins.addAll(additionalPlugins);
        return new DefaultPluginEngine().start(List.copyOf(plugins));
    }
}
