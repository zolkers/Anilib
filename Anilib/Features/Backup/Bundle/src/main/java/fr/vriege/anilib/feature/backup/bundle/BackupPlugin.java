package fr.vriege.anilib.feature.backup.bundle;

import fr.vriege.anilib.feature.backup.BackupCapabilities;
import fr.vriege.anilib.feature.backup.runtime.DefaultBackupService;
import fr.vriege.anilib.feature.backup.ui.BackupUiCapabilities;
import fr.vriege.anilib.feature.backup.ui.DefaultBackupPresentation;
import fr.vriege.anilib.feature.discovery.DiscoveryCapabilities;
import fr.vriege.anilib.feature.library.LibraryCapabilities;
import fr.vriege.anilib.framework.backup.BackupSectionCodec;
import fr.vriege.anilib.foundation.component.ComponentDescriptor;
import fr.vriege.anilib.kernel.AnilibPlugin;
import fr.vriege.anilib.kernel.PluginInstallationContext;
import fr.vriege.anilib.kernel.PluginManifest;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/** Composition unit for local archive management and transactional restore. */
public final class BackupPlugin implements AnilibPlugin {
    private static final PluginManifest MANIFEST = PluginManifest.builder(
                    ComponentDescriptor.of("feature.backup", "Backup and restore", "1.0.0"))
            .requires(LibraryCapabilities.BACKUP_CODEC)
            .requires(DiscoveryCapabilities.BACKUP_CODEC)
            .provides(BackupCapabilities.SERVICE)
            .provides(BackupUiCapabilities.PRESENTATION)
            .build();
    private final Path backupDirectory;

    public BackupPlugin(Path backupDirectory) {
        this.backupDirectory = Objects.requireNonNull(
                backupDirectory,
                "backupDirectory must not be null").toAbsolutePath().normalize();
    }

    @Override
    public PluginManifest manifest() {
        return MANIFEST;
    }

    @Override
    public void install(PluginInstallationContext context) {
        BackupSectionCodec library = context.require(LibraryCapabilities.BACKUP_CODEC);
        BackupSectionCodec discovery = context.require(DiscoveryCapabilities.BACKUP_CODEC);
        DefaultBackupService service = context.own(new DefaultBackupService(
                backupDirectory,
                List.of(library, discovery)));
        context.publish(BackupCapabilities.SERVICE, service);
        context.publish(BackupUiCapabilities.PRESENTATION, new DefaultBackupPresentation(service));
    }
}
