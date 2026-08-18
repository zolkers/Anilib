package fr.vriege.anilib.feature.discovery.bundle;

import fr.vriege.anilib.feature.discovery.DiscoveryCapabilities;
import fr.vriege.anilib.feature.discovery.runtime.DefaultDiscoveryService;
import fr.vriege.anilib.feature.discovery.runtime.DiscoveryBackupCodec;
import fr.vriege.anilib.feature.discovery.runtime.FileSourcePreferenceStore;
import fr.vriege.anilib.feature.discovery.ui.DefaultDiscoveryPresentation;
import fr.vriege.anilib.feature.discovery.ui.DiscoveryUiCapabilities;
import fr.vriege.anilib.feature.library.LibraryCapabilities;
import fr.vriege.anilib.feature.library.LibraryCatalog;
import fr.vriege.anilib.feature.source.SourceCapabilities;
import fr.vriege.anilib.feature.source.SourceRegistry;
import fr.vriege.anilib.foundation.component.ComponentDescriptor;
import fr.vriege.anilib.kernel.AnilibPlugin;
import fr.vriege.anilib.kernel.PluginInstallationContext;
import fr.vriege.anilib.kernel.PluginManifest;

import java.nio.file.Path;
import java.util.Objects;

public final class DiscoveryPlugin implements AnilibPlugin {
    private static final PluginManifest MANIFEST = PluginManifest.builder(
                    ComponentDescriptor.of("feature.discovery", "Browse", "1.0.0"))
            .requires(SourceCapabilities.REGISTRY)
            .requires(LibraryCapabilities.CATALOG)
            .provides(DiscoveryCapabilities.SERVICE)
            .provides(DiscoveryCapabilities.BACKUP_CODEC)
            .provides(DiscoveryUiCapabilities.PRESENTATION)
            .build();

    private final Path preferenceFile;

    public DiscoveryPlugin(Path preferenceFile) {
        this.preferenceFile = Objects.requireNonNull(preferenceFile, "preferenceFile must not be null")
                .toAbsolutePath()
                .normalize();
    }

    @Override
    public PluginManifest manifest() {
        return MANIFEST;
    }

    @Override
    public void install(PluginInstallationContext context) {
        SourceRegistry sources = context.require(SourceCapabilities.REGISTRY);
        LibraryCatalog library = context.require(LibraryCapabilities.CATALOG);
        FileSourcePreferenceStore preferences = new FileSourcePreferenceStore(preferenceFile);
        DefaultDiscoveryService service = new DefaultDiscoveryService(sources, library, preferences);
        context.publish(DiscoveryCapabilities.SERVICE, service);
        context.publish(DiscoveryCapabilities.BACKUP_CODEC, new DiscoveryBackupCodec(preferences));
        context.publish(DiscoveryUiCapabilities.PRESENTATION, new DefaultDiscoveryPresentation(service));
    }
}
