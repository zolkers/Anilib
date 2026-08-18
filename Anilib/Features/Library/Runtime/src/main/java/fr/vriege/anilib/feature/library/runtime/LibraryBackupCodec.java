package fr.vriege.anilib.feature.library.runtime;

import fr.vriege.anilib.feature.library.LibraryCatalog;
import fr.vriege.anilib.feature.library.LibraryItem;
import fr.vriege.anilib.feature.library.LibraryItemId;
import fr.vriege.anilib.framework.backup.BackupCodecException;
import fr.vriege.anilib.framework.backup.BackupSectionCodec;
import fr.vriege.anilib.framework.backup.BackupSectionData;
import fr.vriege.anilib.framework.backup.BackupSectionDetails;
import fr.vriege.anilib.framework.backup.BackupSectionId;
import fr.vriege.anilib.framework.backup.PreparedBackupRestore;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class LibraryBackupCodec implements BackupSectionCodec {
    private static final BackupSectionId SECTION_ID = BackupSectionId.of("library");
    private final LibraryCatalog catalog;

    public LibraryBackupCodec(LibraryCatalog catalog) {
        this.catalog = Objects.requireNonNull(catalog, "catalog must not be null");
    }

    @Override
    public BackupSectionId sectionId() {
        return SECTION_ID;
    }

    @Override
    public String displayName() {
        return "Library";
    }

    @Override
    public int currentVersion() {
        return LibraryFileStore.CURRENT_VERSION;
    }

    @Override
    public BackupSectionData exportSection() {
        List<LibraryItem> items = catalog.snapshot();
        try {
            return new BackupSectionData(LibraryFileStore.encode(items), items.size());
        } catch (IOException exception) {
            throw new BackupCodecException("Unable to encode the Library backup section", exception);
        }
    }

    @Override
    public BackupSectionDetails inspect(int version, byte[] payload) {
        List<LibraryItem> items = decode(version, payload);
        return new BackupSectionDetails(SECTION_ID, displayName(), version, items.size());
    }

    @Override
    public PreparedBackupRestore prepareRestore(int version, byte[] payload) {
        List<LibraryItem> imported = decode(version, payload);
        List<LibraryItem> before = catalog.snapshot();
        Map<LibraryItemId, LibraryItem> merged = new LinkedHashMap<>();
        before.forEach(item -> merged.put(item.id(), item));
        imported.forEach(item -> merged.put(item.id(), item));
        return new LibraryRestore(catalog, before, List.copyOf(merged.values()));
    }

    private List<LibraryItem> decode(int version, byte[] payload) {
        try {
            LibraryFileStore.LoadResult decoded = LibraryFileStore.decode(payload);
            if (decoded.version() != version) {
                throw new BackupCodecException("Library section version does not match its payload");
            }
            return decoded.items();
        } catch (IOException exception) {
            throw new BackupCodecException("Invalid Library backup section", exception);
        }
    }

    private static final class LibraryRestore implements PreparedBackupRestore {
        private final LibraryCatalog catalog;
        private final List<LibraryItem> before;
        private final List<LibraryItem> replacement;
        private boolean committed;

        private LibraryRestore(
                LibraryCatalog catalog,
                List<LibraryItem> before,
                List<LibraryItem> replacement) {
            this.catalog = catalog;
            this.before = before;
            this.replacement = replacement;
        }

        @Override
        public void commit() {
            catalog.replaceAll(replacement);
            committed = true;
        }

        @Override
        public void rollback() {
            if (committed) {
                catalog.replaceAll(before);
                committed = false;
            }
        }
    }
}
