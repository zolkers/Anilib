package fr.vriege.anilib.feature.library;

import fr.vriege.anilib.framework.backup.BackupSectionCodec;
import fr.vriege.anilib.kernel.CapabilityKey;

public final class LibraryCapabilities {
    public static final CapabilityKey<LibraryCatalog> CATALOG =
            CapabilityKey.of("feature.library.catalog", LibraryCatalog.class);
    public static final CapabilityKey<BackupSectionCodec> BACKUP_CODEC =
            CapabilityKey.of("feature.library.backup-codec", BackupSectionCodec.class);

    private LibraryCapabilities() {
    }
}
