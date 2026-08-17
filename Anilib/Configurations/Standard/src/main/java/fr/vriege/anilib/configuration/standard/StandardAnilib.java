package fr.vriege.anilib.configuration.standard;

import fr.vriege.anilib.feature.library.bundle.LibraryPlugin;
import fr.vriege.anilib.feature.localsource.bundle.LocalSourcePlugin;
import fr.vriege.anilib.feature.source.bundle.SourceSdkPlugin;
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
        Objects.requireNonNull(dataDirectory, "dataDirectory must not be null");
        Objects.requireNonNull(additionalPlugins, "additionalPlugins must not be null");
        Path libraryFile = dataDirectory.toAbsolutePath().normalize().resolve("library.anilib");
        Path localContent = dataDirectory.toAbsolutePath().normalize().resolve("local-content");
        List<AnilibPlugin> plugins = new ArrayList<>();
        plugins.add(new LibraryPlugin(libraryFile));
        plugins.add(new SourceSdkPlugin());
        plugins.add(new LocalSourcePlugin(localContent));
        plugins.addAll(additionalPlugins);
        return new DefaultPluginEngine().start(List.copyOf(plugins));
    }
}
