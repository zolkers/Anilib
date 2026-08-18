package fr.vriege.anilib.feature.tracker.bundle;

import fr.vriege.anilib.feature.library.LibraryCapabilities;
import fr.vriege.anilib.feature.library.LibraryCatalog;
import fr.vriege.anilib.feature.tracker.TrackerCapabilities;
import fr.vriege.anilib.feature.tracker.runtime.DefaultTrackerRegistry;
import fr.vriege.anilib.feature.tracker.runtime.DefaultTrackerService;
import fr.vriege.anilib.feature.tracker.runtime.TrackerBackupCodec;
import fr.vriege.anilib.feature.tracker.ui.DefaultTrackerPresentation;
import fr.vriege.anilib.feature.tracker.ui.TrackerUiCapabilities;
import fr.vriege.anilib.foundation.component.ComponentDescriptor;
import fr.vriege.anilib.kernel.AnilibPlugin;
import fr.vriege.anilib.kernel.PluginInstallationContext;
import fr.vriege.anilib.kernel.PluginManifest;

import java.nio.file.Path;
import java.util.Objects;

/** Removable composition unit for tracker registration, durable mirrors, backup, and UI. */
public final class TrackerPlugin implements AnilibPlugin {
    private static final PluginManifest MANIFEST = PluginManifest.builder(
                    ComponentDescriptor.of("feature.tracker", "Tracking", "0.1.0"))
            .requires(LibraryCapabilities.CATALOG)
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
        DefaultTrackerRegistry registry = context.own(new DefaultTrackerRegistry());
        DefaultTrackerService service = new DefaultTrackerService(registry, library, stateFile);
        context.publish(TrackerCapabilities.REGISTRY, registry);
        context.publish(TrackerCapabilities.REGISTRAR, registry);
        context.publish(TrackerCapabilities.SERVICE, service);
        context.publish(TrackerCapabilities.BACKUP_CODEC, new TrackerBackupCodec(service));
        context.publish(TrackerUiCapabilities.PRESENTATION, new DefaultTrackerPresentation(service));
    }
}
