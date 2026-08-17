package fr.vriege.anilib.feature.library.bundle;

import fr.vriege.anilib.feature.library.LibraryCapabilities;
import fr.vriege.anilib.feature.library.core.InMemoryLibraryCatalog;
import fr.vriege.anilib.foundation.component.ComponentDescriptor;
import fr.vriege.anilib.kernel.AnilibPlugin;
import fr.vriege.anilib.kernel.PluginInstallationContext;
import fr.vriege.anilib.kernel.PluginManifest;

/** Single additive composition unit for the Library feature. */
public final class LibraryPlugin implements AnilibPlugin {
    private static final PluginManifest MANIFEST = PluginManifest.builder(
                    ComponentDescriptor.of("feature.library", "Library", "0.1.0"))
            .provides(LibraryCapabilities.CATALOG)
            .build();

    public LibraryPlugin() {
    }

    @Override
    public PluginManifest manifest() {
        return MANIFEST;
    }

    @Override
    public void install(PluginInstallationContext context) {
        context.publish(LibraryCapabilities.CATALOG, new InMemoryLibraryCatalog());
    }
}
