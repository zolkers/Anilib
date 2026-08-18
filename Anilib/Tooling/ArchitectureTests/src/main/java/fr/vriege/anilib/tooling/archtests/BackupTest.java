package fr.vriege.anilib.tooling.archtests;

import fr.vriege.anilib.configuration.standard.StandardAnilib;
import fr.vriege.anilib.feature.backup.BackupCapabilities;
import fr.vriege.anilib.feature.backup.BackupException;
import fr.vriege.anilib.feature.backup.BackupFileSnapshot;
import fr.vriege.anilib.feature.backup.BackupInspection;
import fr.vriege.anilib.feature.backup.BackupRestoreResult;
import fr.vriege.anilib.feature.backup.BackupService;
import fr.vriege.anilib.feature.backup.runtime.DefaultBackupService;
import fr.vriege.anilib.feature.backup.ui.BackupUiCapabilities;
import fr.vriege.anilib.feature.discovery.runtime.DiscoveryBackupCodec;
import fr.vriege.anilib.feature.discovery.runtime.FileSourcePreferenceStore;
import fr.vriege.anilib.feature.library.LibraryCapabilities;
import fr.vriege.anilib.feature.library.LibraryCatalog;
import fr.vriege.anilib.feature.library.LibraryHistoryEntry;
import fr.vriege.anilib.feature.library.LibraryItem;
import fr.vriege.anilib.feature.library.LibraryItemId;
import fr.vriege.anilib.feature.library.LibraryOrigin;
import fr.vriege.anilib.feature.library.LibraryProgress;
import fr.vriege.anilib.feature.library.LibraryTitleMetadata;
import fr.vriege.anilib.feature.library.MediaKind;
import fr.vriege.anilib.feature.library.PublicationStatus;
import fr.vriege.anilib.feature.library.runtime.FileLibraryCatalog;
import fr.vriege.anilib.feature.library.runtime.LibraryBackupCodec;
import fr.vriege.anilib.feature.source.SourceId;
import fr.vriege.anilib.framework.backup.BackupCodecException;
import fr.vriege.anilib.framework.backup.BackupSectionCodec;
import fr.vriege.anilib.framework.backup.BackupSectionData;
import fr.vriege.anilib.framework.backup.BackupSectionDetails;
import fr.vriege.anilib.framework.backup.BackupSectionId;
import fr.vriege.anilib.framework.backup.PreparedBackupRestore;
import fr.vriege.anilib.kernel.StartedAnilib;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Round-trip, checksum, merge, self-owned codec, and rollback checks for backups. */
final class BackupTest {
    private static final Instant BACKUP_TIME = Instant.parse("2026-08-18T09:30:00Z");

    private BackupTest() {
    }

    static int run() {
        Counter counter = new Counter();
        verifiesStandardRoundTrip(counter);
        verifiesFeatureOwnedCodecs(counter);
        verifiesCorruptionIsRejected(counter);
        verifiesFailedRestoreRollsBack(counter);
        return counter.value;
    }

    private static void verifiesStandardRoundTrip(Counter counter) {
        Path directory = temporaryDirectory("anilib-backup-standard");
        LibraryItem original = enrichedItem("backup-title", "Original title");
        LibraryItem extra = enrichedItem("newer-title", "Newer title");
        Path backupPath;
        try {
            try (StartedAnilib application = StandardAnilib.start(directory)) {
                LibraryCatalog library = application.capability(LibraryCapabilities.CATALOG);
                BackupService backups = application.capability(BackupCapabilities.SERVICE);
                library.save(original);
                BackupFileSnapshot created = backups.createBackup();
                backupPath = created.path();
                counter.check(Files.isRegularFile(backupPath), "backup creation must write a local archive");
                counter.check(created.sectionCount() == 4, "standard backup must contain four owned sections");
                counter.check(created.entryCount() == 1, "backup entry count must include the library title");
                BackupInspection inspection = backups.inspect(backupPath);
                counter.check(inspection.sections().stream().allMatch(section -> section.restorable()),
                        "every standard backup section must be restorable");
                counter.check(inspection.sections().stream()
                                .map(section -> section.id().value())
                                .toList()
                                .equals(List.of("library", "playback-state", "source-preferences", "tracking")),
                        "section order must be deterministic");
                counter.check(application.capability(BackupUiCapabilities.PRESENTATION)
                                .backups().size() == 1,
                        "shared Backup presentation must expose local archives");

                library.save(rename(original, "Changed after backup"));
                library.save(extra);
                BackupRestoreResult restored = backups.restore(backupPath);
                counter.check(restored.restoredSections().size() == 4,
                        "restore must commit each installed feature section");
                counter.check(library.find(original.id()).orElseThrow().equals(original),
                        "restore must recover every library field");
                counter.check(library.find(extra.id()).orElseThrow().equals(extra),
                        "restore must merge without deleting newer library entries");
            }
            try (StartedAnilib restarted = StandardAnilib.start(directory)) {
                LibraryCatalog library = restarted.capability(LibraryCapabilities.CATALOG);
                BackupService backups = restarted.capability(BackupCapabilities.SERVICE);
                counter.check(library.find(original.id()).orElseThrow().equals(original),
                        "restored library data must survive product restart");
                counter.check(backups.backups().size() == 1,
                        "local archive list must survive product restart");
                backups.delete(backupPath);
                counter.check(backups.backups().isEmpty(),
                        "managed backup deletion must remove the selected archive");
            }
        } finally {
            deleteDirectory(directory);
        }
    }

    private static void verifiesFeatureOwnedCodecs(Counter counter) {
        Path directory = temporaryDirectory("anilib-backup-codecs");
        try {
            FileLibraryCatalog library = new FileLibraryCatalog(directory.resolve("library.anilib"));
            FileSourcePreferenceStore preferences =
                    new FileSourcePreferenceStore(directory.resolve("preferences.properties"));
            LibraryItem item = enrichedItem("owned-codec", "Owned codec");
            library.save(item);
            preferences.set(SourceId.of("test.source"), "quality", "high");
            try (DefaultBackupService backups = new DefaultBackupService(
                    directory.resolve("backups"),
                    List.of(new LibraryBackupCodec(library), new DiscoveryBackupCodec(preferences)),
                    Clock.fixed(BACKUP_TIME, ZoneOffset.UTC))) {
                Path path = backups.createBackup().path();
                library.save(rename(item, "Mutated"));
                preferences.set(SourceId.of("test.source"), "quality", "low");
                preferences.set(SourceId.of("test.source"), "language", "fr");
                backups.restore(path);
                counter.check(library.find(item.id()).orElseThrow().equals(item),
                        "Library must decode its own versioned section");
                counter.check(preferences.get(SourceId.of("test.source"), "quality", "missing").equals("high"),
                        "Discovery must decode and restore its own source preferences");
                counter.check(preferences.get(SourceId.of("test.source"), "language", "missing").equals("fr"),
                        "source preference restore must preserve newer unrelated keys");
            }
        } finally {
            deleteDirectory(directory);
        }
    }

    private static void verifiesCorruptionIsRejected(Counter counter) {
        Path directory = temporaryDirectory("anilib-backup-corrupt");
        try {
            FileLibraryCatalog library = new FileLibraryCatalog(directory.resolve("library.anilib"));
            LibraryItem item = enrichedItem("checksum-title", "Checksum title");
            library.save(item);
            try (DefaultBackupService backups = new DefaultBackupService(
                    directory.resolve("backups"),
                    List.of(new LibraryBackupCodec(library)),
                    Clock.fixed(BACKUP_TIME, ZoneOffset.UTC))) {
                Path path = backups.createBackup().path();
                byte[] bytes = read(path);
                bytes[bytes.length / 2] ^= 0x01;
                write(path, bytes);
                expectBackupFailure(() -> backups.inspect(path), counter,
                        "archive checksum must reject a modified backup");
                counter.check(library.find(item.id()).orElseThrow().equals(item),
                        "inspection failure must not mutate feature state");
            }
        } finally {
            deleteDirectory(directory);
        }
    }

    private static void verifiesFailedRestoreRollsBack(Counter counter) {
        Path directory = temporaryDirectory("anilib-backup-rollback");
        ValueState first = new ValueState("first-backup");
        ValueState second = new ValueState("second-backup");
        try (DefaultBackupService backups = new DefaultBackupService(
                directory,
                List.of(new ValueCodec("first", first), new ValueCodec("second", second)),
                Clock.fixed(BACKUP_TIME, ZoneOffset.UTC))) {
            Path path = backups.createBackup().path();
            first.value = "first-current";
            second.value = "second-current";
            second.failCommit = true;
            expectBackupFailure(() -> backups.restore(path), counter,
                    "later section failure must fail the restore transaction");
            counter.check(first.value.equals("first-current"),
                    "a committed earlier section must roll back after a later failure");
            counter.check(second.value.equals("second-current"),
                    "a failing section must retain its pre-restore value");
        } finally {
            deleteDirectory(directory);
        }
    }

    private static LibraryItem enrichedItem(String id, String title) {
        return new LibraryItem(
                new LibraryItemId(id),
                title,
                MediaKind.MANGA,
                Instant.parse("2026-08-17T12:00:00Z"),
                Set.of("Archive", "Favourite"),
                true,
                Optional.of(new LibraryProgress(
                        "chapter-4",
                        7L,
                        12L,
                        Instant.parse("2026-08-17T13:00:00Z"))),
                List.of(new LibraryHistoryEntry(
                        "chapter-3",
                        Instant.parse("2026-08-16T20:00:00Z"),
                        10L)),
                new LibraryTitleMetadata(
                        "Complete backup payload",
                        List.of("Author"),
                        List.of("Artist"),
                        PublicationStatus.ONGOING),
                Optional.of(new LibraryOrigin("test.source", "remote-" + id)));
    }

    private static LibraryItem rename(LibraryItem item, String title) {
        return new LibraryItem(
                item.id(),
                title,
                item.kind(),
                item.addedAt(),
                item.categories(),
                item.favorite(),
                item.progress(),
                item.history(),
                item.metadata(),
                item.origin());
    }

    private static Path temporaryDirectory(String prefix) {
        try {
            return Files.createTempDirectory(prefix);
        } catch (IOException exception) {
            throw new AssertionError("Unable to create backup test directory", exception);
        }
    }

    private static byte[] read(Path path) {
        try {
            return Files.readAllBytes(path);
        } catch (IOException exception) {
            throw new AssertionError("Unable to read test backup", exception);
        }
    }

    private static void write(Path path, byte[] bytes) {
        try {
            Files.write(path, bytes);
        } catch (IOException exception) {
            throw new AssertionError("Unable to corrupt test backup", exception);
        }
    }

    private static void expectBackupFailure(Runnable action, Counter counter, String message) {
        try {
            action.run();
            throw new AssertionError(message);
        } catch (BackupException | BackupCodecException expected) {
            counter.value++;
        }
    }

    private static void deleteDirectory(Path directory) {
        if (!Files.exists(directory)) {
            return;
        }
        try (java.util.stream.Stream<Path> entries = Files.walk(directory)) {
            for (Path entry : entries.sorted(java.util.Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(entry);
            }
        } catch (IOException exception) {
            throw new AssertionError("Unable to clean backup test directory", exception);
        }
    }

    private static final class Counter {
        private int value;

        private void check(boolean condition, String message) {
            if (!condition) {
                throw new AssertionError(message);
            }
            value++;
        }
    }

    private static final class ValueState {
        private String value;
        private boolean failCommit;

        private ValueState(String value) {
            this.value = value;
        }
    }

    private static final class ValueCodec implements BackupSectionCodec {
        private final BackupSectionId id;
        private final ValueState state;

        private ValueCodec(String id, ValueState state) {
            this.id = BackupSectionId.of(id);
            this.state = state;
        }

        @Override
        public BackupSectionId sectionId() {
            return id;
        }

        @Override
        public String displayName() {
            return id.value();
        }

        @Override
        public int currentVersion() {
            return 1;
        }

        @Override
        public BackupSectionData exportSection() {
            return new BackupSectionData(state.value.getBytes(StandardCharsets.UTF_8), 1);
        }

        @Override
        public BackupSectionDetails inspect(int version, byte[] payload) {
            decode(version, payload);
            return new BackupSectionDetails(id, displayName(), version, 1);
        }

        @Override
        public PreparedBackupRestore prepareRestore(int version, byte[] payload) {
            String replacement = decode(version, payload);
            String before = state.value;
            return new PreparedBackupRestore() {
                private boolean committed;

                @Override
                public void commit() {
                    if (state.failCommit) {
                        throw new BackupCodecException("Requested test commit failure");
                    }
                    state.value = replacement;
                    committed = true;
                }

                @Override
                public void rollback() {
                    if (committed) {
                        state.value = before;
                        committed = false;
                    }
                }
            };
        }

        private static String decode(int version, byte[] payload) {
            if (version != 1 || payload.length == 0) {
                throw new BackupCodecException("Invalid value section");
            }
            return new String(payload, StandardCharsets.UTF_8);
        }
    }
}
