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
import fr.vriege.anilib.kernel.CapabilityKey;
import fr.vriege.anilib.kernel.PluginInstallationContext;
import fr.vriege.anilib.kernel.PluginManifest;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class BackupPlugin implements AnilibPlugin {
    private final Path backupDirectory;
    private final List<CapabilityKey<BackupSectionCodec>> codecCapabilities;
    private final PluginManifest manifest;

    public BackupPlugin(Path backupDirectory) {
        this(backupDirectory, List.of());
    }

    public BackupPlugin(
            Path backupDirectory,
            List<CapabilityKey<BackupSectionCodec>> additionalCodecs) {
        this.backupDirectory = Objects.requireNonNull(
                backupDirectory,
                "backupDirectory must not be null").toAbsolutePath().normalize();
        List<CapabilityKey<BackupSectionCodec>> capabilities = new ArrayList<>();
        capabilities.add(LibraryCapabilities.BACKUP_CODEC);
        capabilities.add(DiscoveryCapabilities.BACKUP_CODEC);
        capabilities.addAll(Objects.requireNonNull(
                additionalCodecs,
                "additionalCodecs must not be null"));
        Set<CapabilityKey<BackupSectionCodec>> unique = new HashSet<>();
        for (CapabilityKey<BackupSectionCodec> capability : capabilities) {
            if (!unique.add(Objects.requireNonNull(capability, "codec capability must not be null"))) {
                throw new IllegalArgumentException("backup codec capabilities must be unique");
            }
        }
        codecCapabilities = List.copyOf(capabilities);
        PluginManifest.Builder builder = PluginManifest.builder(
                        ComponentDescriptor.of("feature.backup", "Backup and restore", "1.0.0"))
                .provides(BackupCapabilities.SERVICE)
                .provides(BackupUiCapabilities.PRESENTATION);
        codecCapabilities.forEach(builder::requires);
        manifest = builder.build();
    }

    @Override
    public PluginManifest manifest() {
        return manifest;
    }

    @Override
    public void install(PluginInstallationContext context) {
        List<BackupSectionCodec> codecs = codecCapabilities.stream()
                .map(context::require)
                .toList();
        DefaultBackupService service = context.own(new DefaultBackupService(
                backupDirectory,
                codecs));
        context.publish(BackupCapabilities.SERVICE, service);
        context.publish(BackupUiCapabilities.PRESENTATION, new DefaultBackupPresentation(service));
    }
}
