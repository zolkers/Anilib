package fr.vriege.anilib.feature.downloads.bundle;

import fr.vriege.anilib.feature.downloads.DownloadCapabilities;
import fr.vriege.anilib.feature.downloads.DownloadStoragePolicy;
import fr.vriege.anilib.feature.downloads.runtime.DefaultDownloadService;
import fr.vriege.anilib.feature.downloads.runtime.AutomaticDownloadUpdateCoordinator;
import fr.vriege.anilib.feature.downloads.ui.DefaultDownloadPresentation;
import fr.vriege.anilib.feature.downloads.ui.DownloadUiCapabilities;
import fr.vriege.anilib.feature.library.LibraryCapabilities;
import fr.vriege.anilib.feature.library.LibraryCatalog;
import fr.vriege.anilib.feature.network.NetworkCapabilities;
import fr.vriege.anilib.feature.network.NetworkStatus;
import fr.vriege.anilib.feature.reader.ReaderCapabilities;
import fr.vriege.anilib.feature.reader.ReaderContentRegistrar;
import fr.vriege.anilib.feature.source.SourceCapabilities;
import fr.vriege.anilib.feature.source.SourceRegistry;
import fr.vriege.anilib.feature.settings.SettingsCapabilities;
import fr.vriege.anilib.feature.settings.SettingsService;
import fr.vriege.anilib.feature.settings.UnusedDataRegistrar;
import fr.vriege.anilib.feature.updates.LibraryUpdateService;
import fr.vriege.anilib.feature.updates.UpdateCapabilities;
import fr.vriege.anilib.foundation.component.ComponentDescriptor;
import fr.vriege.anilib.kernel.AnilibPlugin;
import fr.vriege.anilib.kernel.PluginInstallationContext;
import fr.vriege.anilib.kernel.PluginManifest;

import java.nio.file.Path;
import java.util.Objects;

public final class DownloadPlugin implements AnilibPlugin {
    private static final PluginManifest MANIFEST = PluginManifest.builder(
                    ComponentDescriptor.of("feature.downloads", "Downloads", "0.1.0"))
            .requires(SourceCapabilities.REGISTRY)
            .requires(LibraryCapabilities.CATALOG)
            .requires(ReaderCapabilities.CONTENT_REGISTRAR)
            .requires(NetworkCapabilities.STATUS)
            .requires(SettingsCapabilities.SERVICE)
            .requires(SettingsCapabilities.UNUSED_DATA_REGISTRAR)
            .requires(UpdateCapabilities.SERVICE)
            .provides(DownloadCapabilities.SERVICE)
            .provides(DownloadUiCapabilities.PRESENTATION)
            .build();

    private final Path storageDirectory;
    private final DownloadStoragePolicy policy;

    public DownloadPlugin(Path storageDirectory) {
        this(storageDirectory, DownloadStoragePolicy.standard());
    }

    public DownloadPlugin(Path storageDirectory, DownloadStoragePolicy policy) {
        this.storageDirectory = Objects.requireNonNull(
                storageDirectory,
                "storageDirectory must not be null").toAbsolutePath().normalize();
        this.policy = Objects.requireNonNull(policy, "policy must not be null");
    }

    @Override
    public PluginManifest manifest() {
        return MANIFEST;
    }

    @Override
    public void install(PluginInstallationContext context) {
        SourceRegistry sources = context.require(SourceCapabilities.REGISTRY);
        LibraryCatalog library = context.require(LibraryCapabilities.CATALOG);
        ReaderContentRegistrar registrar = context.require(ReaderCapabilities.CONTENT_REGISTRAR);
        NetworkStatus network = context.require(NetworkCapabilities.STATUS);
        SettingsService settings = context.require(SettingsCapabilities.SERVICE);
        UnusedDataRegistrar cleanup = context.require(SettingsCapabilities.UNUSED_DATA_REGISTRAR);
        LibraryUpdateService updates = context.require(UpdateCapabilities.SERVICE);
        DefaultDownloadService service = context.own(new DefaultDownloadService(
                sources,
                library,
                storageDirectory,
                policy,
                () -> !settings.snapshot().downloadOnlyOnWifi() || network.allowsLargeTransfers()));
        context.own(new AutomaticDownloadUpdateCoordinator(service, updates));
        context.own(registrar.register(service));
        context.own(cleanup.register("downloads", service::cleanUnusedData));
        context.publish(DownloadCapabilities.SERVICE, service);
        context.publish(DownloadUiCapabilities.PRESENTATION, new DefaultDownloadPresentation(service));
    }
}
