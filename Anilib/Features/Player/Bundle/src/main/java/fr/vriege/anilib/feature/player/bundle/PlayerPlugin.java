package fr.vriege.anilib.feature.player.bundle;

import fr.vriege.anilib.feature.library.LibraryCapabilities;
import fr.vriege.anilib.feature.library.LibraryCatalog;
import fr.vriege.anilib.feature.player.PlayerCapabilities;
import fr.vriege.anilib.feature.player.PlayerBackend;
import fr.vriege.anilib.feature.player.PlayerBackends;
import fr.vriege.anilib.feature.player.runtime.DefaultPlayerService;
import fr.vriege.anilib.feature.player.runtime.PlayerBackupCodec;
import fr.vriege.anilib.feature.player.ui.DefaultPlayerPresentation;
import fr.vriege.anilib.feature.player.ui.PlayerUiCapabilities;
import fr.vriege.anilib.feature.source.SourceCapabilities;
import fr.vriege.anilib.feature.source.SourceRegistry;
import fr.vriege.anilib.feature.settings.SettingsCapabilities;
import fr.vriege.anilib.feature.settings.SettingsService;
import fr.vriege.anilib.foundation.component.ComponentDescriptor;
import fr.vriege.anilib.kernel.AnilibPlugin;
import fr.vriege.anilib.kernel.PluginInstallationContext;
import fr.vriege.anilib.kernel.PluginManifest;

import java.nio.file.Path;
import java.util.Objects;

public final class PlayerPlugin implements AnilibPlugin {
    private static final PluginManifest MANIFEST = PluginManifest.builder(
                    ComponentDescriptor.of("feature.player", "Player", "0.1.0"))
            .requires(SourceCapabilities.REGISTRY)
            .requires(LibraryCapabilities.CATALOG)
            .requires(SettingsCapabilities.SERVICE)
            .provides(PlayerCapabilities.SERVICE)
            .provides(PlayerCapabilities.BACKEND)
            .provides(PlayerCapabilities.BACKUP_CODEC)
            .provides(PlayerUiCapabilities.PRESENTATION)
            .build();
    private final Path stateFile;
    private final PlayerBackend backend;

    public PlayerPlugin(Path stateFile) {
        this(stateFile, PlayerBackends.unavailable());
    }

    public PlayerPlugin(Path stateFile, PlayerBackend backend) {
        this.stateFile = Objects.requireNonNull(
                stateFile,
                "stateFile must not be null").toAbsolutePath().normalize();
        this.backend = Objects.requireNonNull(backend, "backend must not be null");
    }

    @Override
    public PluginManifest manifest() {
        return MANIFEST;
    }

    @Override
    public void install(PluginInstallationContext context) {
        SourceRegistry sources = context.require(SourceCapabilities.REGISTRY);
        LibraryCatalog library = context.require(LibraryCapabilities.CATALOG);
        SettingsService settings = context.require(SettingsCapabilities.SERVICE);
        DefaultPlayerService service = context.own(new DefaultPlayerService(
                sources,
                library,
                stateFile,
                backend,
                () -> !settings.snapshot().incognitoMode()));
        context.publish(PlayerCapabilities.SERVICE, service);
        context.publish(PlayerCapabilities.BACKEND, backend);
        context.publish(PlayerCapabilities.BACKUP_CODEC, new PlayerBackupCodec(service));
        context.publish(PlayerUiCapabilities.PRESENTATION, new DefaultPlayerPresentation(service));
    }
}
