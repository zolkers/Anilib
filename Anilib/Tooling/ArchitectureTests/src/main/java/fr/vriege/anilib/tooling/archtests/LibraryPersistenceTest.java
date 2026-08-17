package fr.vriege.anilib.tooling.archtests;

import fr.vriege.anilib.feature.library.LibraryItem;
import fr.vriege.anilib.feature.library.LibraryItemId;
import fr.vriege.anilib.feature.library.LibraryHistoryEntry;
import fr.vriege.anilib.feature.library.LibraryProgress;
import fr.vriege.anilib.feature.library.LibraryTitleMetadata;
import fr.vriege.anilib.feature.library.MediaKind;
import fr.vriege.anilib.feature.library.PublicationStatus;
import fr.vriege.anilib.feature.library.runtime.FileLibraryCatalog;

import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/** Black-box persistence, atomic replacement, and legacy migration checks. */
final class LibraryPersistenceTest {
    private static final int MAGIC = 0x414E494C;
    private static final int CURRENT_VERSION = 2;

    private LibraryPersistenceTest() {
    }

    static int run() {
        Counter counter = new Counter();
        try {
            roundTripsCurrentFormat(counter);
            migratesVersionZero(counter);
            migratesVersionOne(counter);
        } catch (IOException exception) {
            throw new AssertionError("Library persistence test failed", exception);
        }
        return counter.value;
    }

    private static void roundTripsCurrentFormat(Counter counter) throws IOException {
        Path directory = Files.createTempDirectory("anilib-library-roundtrip");
        Path file = directory.resolve("library.anilib");
        try {
            LibraryItem item = new LibraryItem(
                    new LibraryItemId("roundtrip-item"),
                    "Roundtrip",
                    MediaKind.ANIME,
                    Instant.parse("2026-08-17T12:34:56.123456789Z"),
                    Set.of("Seasonal"))
                    .withFavorite(true)
                    .withProgress(new LibraryProgress(
                            "episode-7",
                            1_234L,
                            1_800L,
                            Instant.parse("2026-08-17T13:00:00.987654321Z")))
                    .recordHistory(new LibraryHistoryEntry(
                            "episode-6",
                            Instant.parse("2026-08-16T20:30:00Z"),
                            1_800L))
                    .recordHistory(new LibraryHistoryEntry(
                            "episode-7",
                            Instant.parse("2026-08-17T13:00:00Z"),
                            1_234L))
                    .withMetadata(new LibraryTitleMetadata(
                            "A persistence test title.",
                            List.of("Test Author"),
                            List.of("Test Artist"),
                            PublicationStatus.ONGOING));
            counter.check(item.favorite(), "favourite state must be expressible");
            counter.check(item.progress().orElseThrow().completion().orElseThrow() > 0.68,
                    "progress must expose a normalized completion");
            counter.check(item.history().size() == 2, "history must retain chronological visits");
            counter.check(item.metadata().publicationStatus() == PublicationStatus.ONGOING,
                    "per-title publication metadata must be typed");
            FileLibraryCatalog catalog = new FileLibraryCatalog(file);
            catalog.save(item);

            FileLibraryCatalog reloaded = new FileLibraryCatalog(file);
            counter.check(reloaded.find(item.id()).orElseThrow().equals(item),
                    "current file format must preserve every library field");
            counter.check(noTemporaryFiles(directory), "atomic save must not leave temporary files");
            counter.check(reloaded.remove(item.id()), "durable catalog must remove existing items");
            counter.check(new FileLibraryCatalog(file).snapshot().isEmpty(),
                    "removal must survive a catalog restart");
        } finally {
            deleteDirectory(directory);
        }
    }

    private static void migratesVersionZero(Counter counter) throws IOException {
        Path directory = Files.createTempDirectory("anilib-library-migration");
        Path file = directory.resolve("library.anilib");
        LibraryItemId id = new LibraryItemId("legacy-item");
        try {
            writeVersionZero(file, id);
            FileLibraryCatalog migrated = new FileLibraryCatalog(file);
            LibraryItem item = migrated.find(id).orElseThrow();
            counter.check(item.title().equals("Legacy title"), "version zero item must be readable");
            counter.check(item.categories().isEmpty(), "migration must initialize missing categories");
            checkEnrichedDefaults(counter, item, "version zero");
            counter.check(readVersion(file) == CURRENT_VERSION,
                    "opening a legacy catalog must atomically rewrite the current version");
            counter.check(noTemporaryFiles(directory), "migration must not leave temporary files");
        } finally {
            deleteDirectory(directory);
        }
    }

    private static void migratesVersionOne(Counter counter) throws IOException {
        Path directory = Files.createTempDirectory("anilib-library-v1-migration");
        Path file = directory.resolve("library.anilib");
        LibraryItemId id = new LibraryItemId("version-one-item");
        try {
            writeVersionOne(file, id);
            LibraryItem item = new FileLibraryCatalog(file).find(id).orElseThrow();
            counter.check(item.categories().equals(Set.of("Archive", "Favourite")),
                    "version one categories must survive migration");
            checkEnrichedDefaults(counter, item, "version one");
            counter.check(readVersion(file) == CURRENT_VERSION,
                    "opening a version one catalog must rewrite version two");
        } finally {
            deleteDirectory(directory);
        }
    }

    private static void writeVersionZero(Path file, LibraryItemId id) throws IOException {
        try (DataOutputStream output = new DataOutputStream(
                new BufferedOutputStream(Files.newOutputStream(file)))) {
            output.writeInt(MAGIC);
            output.writeInt(0);
            output.writeInt(1);
            output.writeUTF(id.value());
            output.writeUTF("Legacy title");
            output.writeUTF(MediaKind.MANGA.name());
            output.writeLong(Instant.parse("2025-01-02T03:04:05Z").toEpochMilli());
        }
    }

    private static void writeVersionOne(Path file, LibraryItemId id) throws IOException {
        Instant addedAt = Instant.parse("2026-01-02T03:04:05.123456789Z");
        try (DataOutputStream output = new DataOutputStream(
                new BufferedOutputStream(Files.newOutputStream(file)))) {
            output.writeInt(MAGIC);
            output.writeInt(1);
            output.writeInt(1);
            output.writeUTF(id.value());
            output.writeUTF("Version one title");
            output.writeUTF(MediaKind.NOVEL.name());
            output.writeLong(addedAt.getEpochSecond());
            output.writeInt(addedAt.getNano());
            output.writeInt(2);
            output.writeUTF("Archive");
            output.writeUTF("Favourite");
        }
    }

    private static void checkEnrichedDefaults(Counter counter, LibraryItem item, String version) {
        counter.check(!item.favorite(), version + " migration must default favourite to false");
        counter.check(item.progress().isEmpty(), version + " migration must initialize progress");
        counter.check(item.history().isEmpty(), version + " migration must initialize history");
        counter.check(item.metadata().equals(LibraryTitleMetadata.empty()),
                version + " migration must initialize title metadata");
    }

    private static int readVersion(Path file) throws IOException {
        try (DataInputStream input = new DataInputStream(Files.newInputStream(file))) {
            if (input.readInt() != MAGIC) {
                throw new AssertionError("Migrated file signature changed");
            }
            return input.readInt();
        }
    }

    private static boolean noTemporaryFiles(Path directory) throws IOException {
        try (Stream<Path> entries = Files.list(directory)) {
            return entries.noneMatch(path -> path.getFileName().toString().endsWith(".tmp"));
        }
    }

    private static void deleteDirectory(Path directory) throws IOException {
        try (Stream<Path> entries = Files.list(directory)) {
            for (Path entry : entries.toList()) {
                Files.deleteIfExists(entry);
            }
        }
        Files.deleteIfExists(directory);
    }

    private static final class Counter {
        private int value;

        private void check(boolean condition, String message) {
            value++;
            if (!condition) {
                throw new AssertionError(message);
            }
        }
    }
}
