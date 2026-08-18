package fr.vriege.anilib.feature.library.bundle;

import fr.vriege.anilib.feature.library.LibraryCapabilities;
import fr.vriege.anilib.feature.library.LibraryCatalog;
import fr.vriege.anilib.feature.library.runtime.FileLibraryCatalog;
import fr.vriege.anilib.feature.library.runtime.LibraryBackupCodec;
import fr.vriege.anilib.feature.library.ui.DefaultLibraryPresentation;
import fr.vriege.anilib.feature.library.ui.LibraryUiCapabilities;
import fr.vriege.anilib.foundation.component.ComponentDescriptor;
import fr.vriege.anilib.kernel.AnilibPlugin;
import fr.vriege.anilib.kernel.PluginInstallationContext;
import fr.vriege.anilib.kernel.PluginManifest;

import java.nio.file.Path;

public final class LibraryPlugin implements AnilibPlugin {
    private static final PluginManifest MANIFEST = PluginManifest.builder(
                    ComponentDescriptor.of("feature.library", "Library", "0.1.0"))
            .provides(LibraryCapabilities.CATALOG)
            .provides(LibraryCapabilities.BACKUP_CODEC)
            .provides(LibraryUiCapabilities.PRESENTATION)
            .build();
    private final Path storageFile;

    public LibraryPlugin(Path storageFile) {
        this.storageFile = storageFile.toAbsolutePath().normalize();
    }

    @Override
    public PluginManifest manifest() {
        return MANIFEST;
    }

    @Override
    public void install(PluginInstallationContext context) {
        LibraryCatalog catalog = new FileLibraryCatalog(storageFile);
        context.publish(LibraryCapabilities.CATALOG, catalog);
        context.publish(LibraryCapabilities.BACKUP_CODEC, new LibraryBackupCodec(catalog));
        context.publish(LibraryUiCapabilities.PRESENTATION, new DefaultLibraryPresentation(catalog));
    }
}
