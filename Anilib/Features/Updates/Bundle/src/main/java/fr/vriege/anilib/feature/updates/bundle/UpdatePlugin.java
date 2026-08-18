package fr.vriege.anilib.feature.updates.bundle;

import fr.vriege.anilib.feature.library.LibraryCapabilities;
import fr.vriege.anilib.feature.library.LibraryCatalog;
import fr.vriege.anilib.feature.network.NetworkCapabilities;
import fr.vriege.anilib.feature.network.NetworkStatus;
import fr.vriege.anilib.feature.settings.SettingsCapabilities;
import fr.vriege.anilib.feature.settings.SettingsService;
import fr.vriege.anilib.feature.settings.UnusedDataRegistrar;
import fr.vriege.anilib.feature.source.SourceCapabilities;
import fr.vriege.anilib.feature.source.SourceRegistry;
import fr.vriege.anilib.feature.updates.LibraryUpdateNotifier;
import fr.vriege.anilib.feature.updates.UpdateCapabilities;
import fr.vriege.anilib.feature.updates.runtime.DefaultLibraryUpdateService;
import fr.vriege.anilib.feature.updates.runtime.UpdateBackupCodec;
import fr.vriege.anilib.feature.updates.ui.DefaultUpdatePresentation;
import fr.vriege.anilib.feature.updates.ui.UpdateUiCapabilities;
import fr.vriege.anilib.foundation.component.ComponentDescriptor;
import fr.vriege.anilib.kernel.AnilibPlugin;
import fr.vriege.anilib.kernel.PluginInstallationContext;
import fr.vriege.anilib.kernel.PluginManifest;

import java.nio.file.Path;
import java.util.Objects;

public final class UpdatePlugin implements AnilibPlugin {
    private static final PluginManifest MANIFEST = PluginManifest.builder(
                    ComponentDescriptor.of("feature.updates", "Library updates", "0.1.0"))
            .requires(LibraryCapabilities.CATALOG)
            .requires(SourceCapabilities.REGISTRY)
            .requires(NetworkCapabilities.STATUS)
            .requires(SettingsCapabilities.SERVICE)
            .requires(SettingsCapabilities.UNUSED_DATA_REGISTRAR)
            .provides(UpdateCapabilities.SERVICE)
            .provides(UpdateCapabilities.NOTIFIER)
            .provides(UpdateCapabilities.BACKUP_CODEC)
            .provides(UpdateUiCapabilities.PRESENTATION)
            .build();
    private final Path stateFile;
    private final LibraryUpdateNotifier notifier;

    public UpdatePlugin(Path stateFile, LibraryUpdateNotifier notifier) {
        this.stateFile = Objects.requireNonNull(
                stateFile,
                "stateFile must not be null").toAbsolutePath().normalize();
        this.notifier = Objects.requireNonNull(notifier, "notifier must not be null");
    }

    @Override
    public PluginManifest manifest() {
        return MANIFEST;
    }

    @Override
    public void install(PluginInstallationContext context) {
        LibraryCatalog library = context.require(LibraryCapabilities.CATALOG);
        SourceRegistry sources = context.require(SourceCapabilities.REGISTRY);
        NetworkStatus network = context.require(NetworkCapabilities.STATUS);
        SettingsService settings = context.require(SettingsCapabilities.SERVICE);
        UnusedDataRegistrar cleanup = context.require(SettingsCapabilities.UNUSED_DATA_REGISTRAR);
        DefaultLibraryUpdateService service = context.own(new DefaultLibraryUpdateService(
                library,
                sources,
                notifier,
                stateFile,
                () -> !settings.snapshot().updateOnlyOnWifi() || network.allowsLargeTransfers()));
        context.own(cleanup.register("updates", service::cleanUnusedData));
        context.publish(UpdateCapabilities.SERVICE, service);
        context.publish(UpdateCapabilities.NOTIFIER, notifier);
        context.publish(UpdateCapabilities.BACKUP_CODEC, new UpdateBackupCodec(service));
        context.publish(UpdateUiCapabilities.PRESENTATION, new DefaultUpdatePresentation(service));
    }
}
