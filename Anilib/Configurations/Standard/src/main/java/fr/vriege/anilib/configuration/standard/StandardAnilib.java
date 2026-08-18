package fr.vriege.anilib.configuration.standard;

import fr.vriege.anilib.feature.library.bundle.LibraryPlugin;
import fr.vriege.anilib.feature.discovery.bundle.DiscoveryPlugin;
import fr.vriege.anilib.feature.extensionrepository.bundle.ExtensionRepositoryPlugin;
import fr.vriege.anilib.feature.localsource.bundle.LocalSourcePlugin;
import fr.vriege.anilib.feature.network.bundle.NetworkPlugin;
import fr.vriege.anilib.feature.settings.bundle.SettingsPlugin;
import fr.vriege.anilib.feature.reader.bundle.ReaderPlugin;
import fr.vriege.anilib.feature.downloads.bundle.DownloadPlugin;
import fr.vriege.anilib.feature.player.PlayerCapabilities;
import fr.vriege.anilib.feature.player.PlayerBackend;
import fr.vriege.anilib.feature.player.PlayerBackends;
import fr.vriege.anilib.feature.player.bundle.PlayerPlugin;
import fr.vriege.anilib.feature.tracker.TrackerCapabilities;
import fr.vriege.anilib.feature.tracker.bundle.TrackerPlugin;
import fr.vriege.anilib.feature.updates.LibraryUpdateNotifier;
import fr.vriege.anilib.feature.updates.LibraryUpdateNotifiers;
import fr.vriege.anilib.feature.updates.UpdateCapabilities;
import fr.vriege.anilib.feature.updates.bundle.UpdatePlugin;
import fr.vriege.anilib.feature.backup.bundle.BackupPlugin;
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
        return start(
                dataDirectory,
                new UrlConnectionHttpTransport(),
                PlayerBackends.unavailable(),
                LibraryUpdateNotifiers.silent(),
                additionalPlugins);
    }

    public static StartedAnilib start(
            Path dataDirectory,
            HttpTransport httpTransport,
            Collection<? extends AnilibPlugin> additionalPlugins) {
        return start(
                dataDirectory,
                httpTransport,
                PlayerBackends.unavailable(),
                LibraryUpdateNotifiers.silent(),
                additionalPlugins);
    }

    public static StartedAnilib start(
            Path dataDirectory,
            HttpTransport httpTransport,
            LibraryUpdateNotifier updateNotifier,
            Collection<? extends AnilibPlugin> additionalPlugins) {
        return start(
                dataDirectory,
                httpTransport,
                PlayerBackends.unavailable(),
                updateNotifier,
                additionalPlugins);
    }

    public static StartedAnilib start(
            Path dataDirectory,
            HttpTransport httpTransport,
            PlayerBackend playerBackend,
            Collection<? extends AnilibPlugin> additionalPlugins) {
        return start(
                dataDirectory,
                httpTransport,
                playerBackend,
                LibraryUpdateNotifiers.silent(),
                additionalPlugins);
    }

    public static StartedAnilib start(
            Path dataDirectory,
            HttpTransport httpTransport,
            PlayerBackend playerBackend,
            LibraryUpdateNotifier updateNotifier,
            Collection<? extends AnilibPlugin> additionalPlugins) {
        Objects.requireNonNull(dataDirectory, "dataDirectory must not be null");
        Objects.requireNonNull(httpTransport, "httpTransport must not be null");
        Objects.requireNonNull(playerBackend, "playerBackend must not be null");
        Objects.requireNonNull(updateNotifier, "updateNotifier must not be null");
        Objects.requireNonNull(additionalPlugins, "additionalPlugins must not be null");
        Path libraryFile = dataDirectory.toAbsolutePath().normalize().resolve("library.anilib");
        Path localContent = dataDirectory.toAbsolutePath().normalize().resolve("local-content");
        Path httpCache = dataDirectory.toAbsolutePath().normalize().resolve("http-cache");
        Path settings = dataDirectory.toAbsolutePath().normalize().resolve("settings.properties");
        Path sourcePreferences = dataDirectory.toAbsolutePath().normalize().resolve("source-preferences.properties");
        Path extensionRepositories = dataDirectory.toAbsolutePath().normalize().resolve("extension-repositories.txt");
        Path downloads = dataDirectory.toAbsolutePath().normalize().resolve("downloads");
        Path playbackState = dataDirectory.toAbsolutePath().normalize().resolve("playback-state.anilib");
        Path trackingState = dataDirectory.toAbsolutePath().normalize().resolve("tracking.anilib");
        Path updateState = dataDirectory.toAbsolutePath().normalize().resolve("library-updates.anilib");
        Path backups = dataDirectory.toAbsolutePath().normalize().resolve("backups");
        List<AnilibPlugin> plugins = new ArrayList<>();
        plugins.add(new LibraryPlugin(libraryFile));
        plugins.add(new SourceSdkPlugin());
        plugins.add(new LocalSourcePlugin(localContent));
        plugins.add(new NetworkPlugin(httpCache, httpTransport));
        plugins.add(new SettingsPlugin(settings));
        plugins.add(new DiscoveryPlugin(sourcePreferences));
        plugins.add(new ExtensionRepositoryPlugin(extensionRepositories));
        plugins.add(new ReaderPlugin());
        plugins.add(new DownloadPlugin(downloads));
        plugins.add(new PlayerPlugin(playbackState, playerBackend));
        plugins.add(new TrackerPlugin(trackingState));
        plugins.add(new UpdatePlugin(updateState, updateNotifier));
        plugins.add(new BackupPlugin(
                backups,
                List.of(
                        PlayerCapabilities.BACKUP_CODEC,
                        TrackerCapabilities.BACKUP_CODEC,
                        UpdateCapabilities.BACKUP_CODEC)));
        plugins.addAll(additionalPlugins);
        return new DefaultPluginEngine().start(List.copyOf(plugins));
    }
}
