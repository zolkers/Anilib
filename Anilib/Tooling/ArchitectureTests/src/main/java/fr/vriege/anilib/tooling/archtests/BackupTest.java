package fr.vriege.anilib.tooling.archtests;

import fr.vriege.anilib.configuration.standard.StandardAnilib;
import fr.vriege.anilib.feature.backup.BackupCapabilities;
import fr.vriege.anilib.feature.backup.BackupException;
import fr.vriege.anilib.feature.backup.BackupFileSnapshot;
import fr.vriege.anilib.feature.backup.BackupInspection;
import fr.vriege.anilib.feature.backup.BackupPolicy;
import fr.vriege.anilib.feature.backup.BackupSchedule;
import fr.vriege.anilib.feature.backup.BackupRestoreResult;
import fr.vriege.anilib.feature.backup.BackupService;
import fr.vriege.anilib.feature.backup.AniyomiBackupImportResult;
import fr.vriege.anilib.feature.backup.AniyomiBackupInspection;
import fr.vriege.anilib.feature.backup.runtime.DefaultBackupService;
import fr.vriege.anilib.feature.backup.ui.BackupImportFormat;
import fr.vriege.anilib.feature.backup.ui.BackupUiCapabilities;
import fr.vriege.anilib.feature.backup.ui.DefaultBackupPresentation;
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
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.zip.GZIPOutputStream;
import java.util.Comparator;
import java.util.stream.Stream;

final class BackupTest {
    private static final Instant BACKUP_TIME = Instant.parse("2026-08-18T09:30:00Z");

    private BackupTest() {
    }

    static int run() {
        Counter counter = new Counter();
        verifiesStandardRoundTrip(counter);
        verifiesFeatureOwnedCodecs(counter);
        verifiesAutomaticPolicyAndExport(counter);
        verifiesAniyomiBackupImport(counter);
        verifiesCorruptionIsRejected(counter);
        verifiesFailedRestoreRollsBack(counter);
        return counter.value;
    }

    private static void verifiesAutomaticPolicyAndExport(Counter counter) {
        Path directory = temporaryDirectory("anilib-backup-policy");
        Path managed = directory.resolve("managed");
        Path selected = directory.resolve("selected-destination");
        Path exported = directory.resolve("exports");
        try {
            FileLibraryCatalog library = new FileLibraryCatalog(directory.resolve("library.anilib"));
            library.save(enrichedItem("policy-title", "Policy title"));
            BackupSectionCodec codec = new LibraryBackupCodec(library);
            try (DefaultBackupService backups = new DefaultBackupService(
                    managed,
                    List.of(codec),
                    Clock.fixed(BACKUP_TIME, ZoneOffset.UTC))) {
                BackupPolicy policy = new BackupPolicy(
                        BackupSchedule.DAILY,
                        2,
                        Set.of(codec.sectionId()),
                        selected);
                backups.savePolicy(policy);
                counter.check(backups.backups().size() == 1
                                && backups.backups().getFirst().sectionCount() == 1,
                        "enabling a due schedule must create only the selected content in its destination");
                backups.createBackup();
                backups.createBackup();
                counter.check(backups.backups().size() == 2,
                        "backup retention must delete archives older than the configured count");
                Path copy = backups.export(backups.backups().getFirst().path(), exported);
                counter.check(Files.isRegularFile(copy) && copy.getParent().equals(exported),
                        "backup export must copy a managed archive to a user-selected native directory");
            }
            try (DefaultBackupService restarted = new DefaultBackupService(
                    managed,
                    List.of(codec),
                    Clock.fixed(BACKUP_TIME, ZoneOffset.UTC))) {
                counter.check(restarted.policy().destination().equals(selected)
                                && restarted.policy().retentionCount() == 2
                                && restarted.runAutomaticBackupIfDue().isEmpty(),
                        "backup schedule, destination, content, retention, and last run must survive restart");
            }
        } finally {
            deleteDirectory(directory);
        }
    }

    private static void verifiesAniyomiBackupImport(Counter counter) {
        Path directory = temporaryDirectory("anilib-backup-aniyomi");
        try {
            FileLibraryCatalog library = new FileLibraryCatalog(directory.resolve("library.anilib"));
            Path source = directory.resolve("aniyomi.proto.gz");
            write(source, gzip(aniyomiBackupFixture()));
            try (DefaultBackupService backups = new DefaultBackupService(
                    directory.resolve("backups"),
                    List.of(new LibraryBackupCodec(library)),
                    library,
                    Clock.fixed(BACKUP_TIME, ZoneOffset.UTC))) {
                AniyomiBackupInspection inspection = backups.inspectAniyomi(source);
                counter.check(new DefaultBackupPresentation(backups).inspectImport(source).format()
                                == BackupImportFormat.ANIYOMI,
                        "unified backup import must automatically detect an Aniyomi archive");
                counter.check(inspection.mangaCount() == 1 && inspection.animeCount() == 1,
                        "Aniyomi preview must count manga and anime independently");
                counter.check(inspection.categoryCount() == 2,
                        "Aniyomi preview must expose both category collections");
                counter.check(inspection.historyCount() == 2 && inspection.progressCount() == 2,
                        "Aniyomi preview must expose restorable history and progress");
                counter.check(inspection.skippedEntryCount() == 2,
                        "Aniyomi preview must disclose unsupported tracker and preference entries");

                AniyomiBackupImportResult imported = backups.importAniyomi(source);
                counter.check(imported.createdCount() == 2 && imported.updatedCount() == 0,
                        "first Aniyomi import must create both titles");
                LibraryItem manga = library.snapshot().stream()
                        .filter(item -> item.kind() == MediaKind.MANGA)
                        .findFirst()
                        .orElseThrow();
                LibraryItem anime = library.snapshot().stream()
                        .filter(item -> item.kind() == MediaKind.ANIME)
                        .findFirst()
                        .orElseThrow();
                counter.check(manga.origin().orElseThrow().equals(new LibraryOrigin("aniyomi.42", "/manga/one")),
                        "Aniyomi manga origin must retain its numeric source identity and URL");
                counter.check(anime.origin().orElseThrow().equals(new LibraryOrigin("aniyomi.84", "/anime/one")),
                        "Aniyomi anime origin must retain its numeric source identity and URL");
                counter.check(manga.categories().equals(Set.of("Manga shelf"))
                                && anime.categories().equals(Set.of("Anime shelf")),
                        "Aniyomi category order references must resolve to names");
                counter.check(manga.progress().orElseThrow().position() == 7
                                && anime.progress().orElseThrow().extent() == 120,
                        "Aniyomi chapter pages and episode seconds must become shared progress");
                counter.check(manga.metadata().authors().equals(List.of("Manga Author"))
                                && manga.metadata().publicationStatus() == PublicationStatus.ONGOING,
                        "Aniyomi title metadata must map to the Anilib library model");

                library.save(manga.withCategories(Set.of("Manga shelf", "Local category")));
                AniyomiBackupImportResult repeated = backups.importAniyomi(source);
                LibraryItem merged = library.find(manga.id()).orElseThrow();
                counter.check(repeated.createdCount() == 0 && repeated.updatedCount() == 2,
                        "repeated Aniyomi import must update matching origins without duplicates");
                counter.check(merged.categories().contains("Local category") && library.snapshot().size() == 2,
                        "Aniyomi merge must preserve newer local categories and unrelated titles");
            }
        } finally {
            deleteDirectory(directory);
        }
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
                counter.check(created.sectionCount() == 5, "standard backup must contain five owned sections");
                counter.check(created.entryCount() == 1, "backup entry count must include the library title");
                BackupInspection inspection = backups.inspect(backupPath);
                counter.check(inspection.sections().stream().allMatch(section -> section.restorable()),
                        "every standard backup section must be restorable");
                counter.check(inspection.sections().stream()
                                .map(section -> section.id().value())
                                .toList()
                                .equals(List.of(
                                        "library",
                                        "library-updates",
                                        "playback-state",
                                        "source-preferences",
                                        "tracking")),
                        "section order must be deterministic");
                counter.check(application.capability(BackupUiCapabilities.PRESENTATION)
                                .backups().size() == 1,
                        "shared Backup presentation must expose local archives");
                counter.check(application.capability(BackupUiCapabilities.PRESENTATION)
                                .inspectImport(backupPath).format() == BackupImportFormat.ANILIB,
                        "unified backup import must automatically detect a native Anilib archive");

                library.save(rename(original, "Changed after backup"));
                library.save(extra);
                BackupRestoreResult restored = backups.restore(backupPath);
                counter.check(restored.restoredSections().size() == 5,
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

    private static byte[] aniyomiBackupFixture() {
        byte[] mangaCategory = proto(
                stringField(1, "Manga shelf"),
                varintField(2, 2));
        byte[] animeCategory = proto(
                stringField(1, "Anime shelf"),
                varintField(2, 4));
        byte[] chapter = proto(
                stringField(1, "/chapter/one"),
                stringField(2, "Chapter 1"),
                varintField(6, 7),
                varintField(7, 1_700_000_000_000L),
                floatField(9, 1.0F));
        byte[] mangaHistory = proto(
                stringField(1, "/chapter/one"),
                varintField(2, 1_700_000_100_000L));
        byte[] manga = proto(
                varintField(1, 42),
                stringField(2, "/manga/one"),
                stringField(3, "Manga One"),
                stringField(5, "Manga Author"),
                stringField(6, "Manga description"),
                varintField(8, 1),
                varintField(13, 1_699_000_000_000L),
                messageField(16, chapter),
                messageField(17, rawVarint(2)),
                messageField(18, new byte[0]),
                varintField(100, 1),
                messageField(104, mangaHistory));
        byte[] episode = proto(
                stringField(1, "/episode/three"),
                stringField(2, "Episode 3"),
                varintField(6, 30),
                varintField(7, 1_700_100_000_000L),
                floatField(9, 3.0F),
                varintField(16, 120));
        byte[] animeHistory = proto(
                stringField(1, "/episode/three"),
                varintField(2, 1_700_100_100_000L));
        byte[] anime = proto(
                varintField(1, 84),
                stringField(2, "/anime/one"),
                stringField(3, "Anime One"),
                varintField(13, 1_699_100_000_000L),
                messageField(16, episode),
                varintField(17, 4),
                varintField(100, 1),
                messageField(104, animeHistory));
        return proto(
                messageField(1, manga),
                messageField(2, mangaCategory),
                messageField(104, new byte[0]),
                varintField(500, 0),
                messageField(501, anime),
                messageField(502, animeCategory));
    }

    private static byte[] gzip(byte[] payload) {
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            try (GZIPOutputStream gzip = new GZIPOutputStream(output)) {
                gzip.write(payload);
            }
            return output.toByteArray();
        } catch (IOException exception) {
            throw new AssertionError("Unable to create Aniyomi gzip fixture", exception);
        }
    }

    private static byte[] proto(byte[]... fields) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        for (byte[] field : fields) {
            output.writeBytes(field);
        }
        return output.toByteArray();
    }

    private static byte[] stringField(int number, String value) {
        return messageField(number, value.getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] messageField(int number, byte[] value) {
        return proto(rawVarint(((long) number << 3) | 2), rawVarint(value.length), value);
    }

    private static byte[] varintField(int number, long value) {
        return proto(rawVarint((long) number << 3), rawVarint(value));
    }

    private static byte[] floatField(int number, float value) {
        int bits = Float.floatToRawIntBits(value);
        return proto(
                rawVarint(((long) number << 3) | 5),
                new byte[] {
                        (byte) bits,
                        (byte) (bits >>> 8),
                        (byte) (bits >>> 16),
                        (byte) (bits >>> 24)});
    }

    private static byte[] rawVarint(long value) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        long remaining = value;
        do {
            int next = (int) (remaining & 0x7f);
            remaining >>>= 7;
            output.write(remaining == 0 ? next : next | 0x80);
        } while (remaining != 0);
        return output.toByteArray();
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
        try (Stream<Path> entries = Files.walk(directory)) {
            for (Path entry : entries.sorted(Comparator.reverseOrder()).toList()) {
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
