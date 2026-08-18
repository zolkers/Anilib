package fr.vriege.anilib.feature.tracker.bundle;

import fr.vriege.anilib.feature.library.LibraryCapabilities;
import fr.vriege.anilib.feature.library.LibraryCatalog;
import fr.vriege.anilib.feature.tracker.TrackerCapabilities;
import fr.vriege.anilib.feature.tracker.runtime.DefaultTrackerRegistry;
import fr.vriege.anilib.feature.tracker.runtime.DefaultTrackerService;
import fr.vriege.anilib.feature.tracker.runtime.TrackerBackupCodec;
import fr.vriege.anilib.feature.tracker.ui.DefaultTrackerPresentation;
import fr.vriege.anilib.feature.tracker.ui.TrackerUiCapabilities;
import fr.vriege.anilib.feature.settings.SettingsCapabilities;
import fr.vriege.anilib.feature.settings.UnusedDataRegistrar;
import fr.vriege.anilib.foundation.component.ComponentDescriptor;
import fr.vriege.anilib.kernel.AnilibPlugin;
import fr.vriege.anilib.kernel.PluginInstallationContext;
import fr.vriege.anilib.kernel.PluginManifest;

import java.nio.file.Path;
import java.util.Objects;

public final class TrackerPlugin implements AnilibPlugin {
    private static final PluginManifest MANIFEST = PluginManifest.builder(
                    ComponentDescriptor.of("feature.tracker", "Tracking", "0.1.0"))
            .requires(LibraryCapabilities.CATALOG)
            .requires(SettingsCapabilities.UNUSED_DATA_REGISTRAR)
            .provides(TrackerCapabilities.REGISTRY)
            .provides(TrackerCapabilities.REGISTRAR)
            .provides(TrackerCapabilities.SERVICE)
            .provides(TrackerCapabilities.BACKUP_CODEC)
            .provides(TrackerUiCapabilities.PRESENTATION)
            .build();
    private final Path stateFile;

    public TrackerPlugin(Path stateFile) {
        this.stateFile = Objects.requireNonNull(
                stateFile,
                "stateFile must not be null").toAbsolutePath().normalize();
    }

    @Override
    public PluginManifest manifest() {
        return MANIFEST;
    }

    @Override
    public void install(PluginInstallationContext context) {
        LibraryCatalog library = context.require(LibraryCapabilities.CATALOG);
        UnusedDataRegistrar cleanup = context.require(SettingsCapabilities.UNUSED_DATA_REGISTRAR);
        DefaultTrackerRegistry registry = context.own(new DefaultTrackerRegistry());
        DefaultTrackerService service = context.own(new DefaultTrackerService(registry, library, stateFile));
        context.own(cleanup.register("tracking", service::cleanUnusedData));
        context.publish(TrackerCapabilities.REGISTRY, registry);
        context.publish(TrackerCapabilities.REGISTRAR, registry);
        context.publish(TrackerCapabilities.SERVICE, service);
        context.publish(TrackerCapabilities.BACKUP_CODEC, new TrackerBackupCodec(service));
        context.publish(TrackerUiCapabilities.PRESENTATION, new DefaultTrackerPresentation(service));
    }
}
