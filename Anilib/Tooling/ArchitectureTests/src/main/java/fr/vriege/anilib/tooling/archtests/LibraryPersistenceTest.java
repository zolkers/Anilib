package fr.vriege.anilib.tooling.archtests;

import fr.vriege.anilib.feature.library.LibraryCategory;
import fr.vriege.anilib.feature.library.LibraryCategoryScope;
import fr.vriege.anilib.feature.library.LibraryCategoryUpdatePolicy;
import fr.vriege.anilib.feature.library.LibraryConfigurationSnapshot;
import fr.vriege.anilib.feature.library.LibraryDisplayDensity;
import fr.vriege.anilib.feature.library.LibraryDisplayMode;
import fr.vriege.anilib.feature.library.LibraryDisplayPreferences;
import fr.vriege.anilib.feature.library.LibraryHistoryEntry;
import fr.vriege.anilib.feature.library.LibraryItem;
import fr.vriege.anilib.feature.library.LibraryItemId;
import fr.vriege.anilib.feature.library.LibraryOrigin;
import fr.vriege.anilib.feature.library.LibraryProgress;
import fr.vriege.anilib.feature.library.LibrarySort;
import fr.vriege.anilib.feature.library.LibraryTitleMetadata;
import fr.vriege.anilib.feature.library.MediaKind;
import fr.vriege.anilib.feature.library.PublicationStatus;
import fr.vriege.anilib.feature.library.runtime.FileLibraryCatalog;
import fr.vriege.anilib.feature.library.runtime.FileLibraryConfiguration;

import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

final class LibraryPersistenceTest {
    private static final int MAGIC = 0x414E494C;
    private static final int CURRENT_VERSION = 5;
    private static final int CONFIGURATION_MAGIC = 0x414E4C43;
    private static final int CURRENT_CONFIGURATION_VERSION = 2;

    private LibraryPersistenceTest() {
    }

    static int run() {
        Counter counter = new Counter();
        try {
            roundTripsCurrentFormat(counter);
            roundTripsLibraryConfiguration(counter);
            migratesVersionOneLibraryConfiguration(counter);
            migratesVersionZero(counter);
            migratesVersionOne(counter);
            migratesVersionTwo(counter);
            migratesVersionThree(counter);
        } catch (IOException exception) {
            throw new AssertionError("Library persistence test failed", exception);
        }
        return counter.value;
    }

    private static void roundTripsLibraryConfiguration(Counter counter) throws IOException {
        Path directory = Files.createTempDirectory("anilib-library-configuration");
        Path file = directory.resolve("library.configuration");
        try {
            FileLibraryConfiguration configuration = new FileLibraryConfiguration(file);
            LibraryDisplayPreferences preferences = new LibraryDisplayPreferences(
                    LibraryDisplayMode.LIST,
                    LibraryDisplayDensity.COMPACT,
                    LibrarySort.ADDED_NEWEST,
                    Optional.of("Seasonal"));
            LibraryConfigurationSnapshot snapshot = new LibraryConfigurationSnapshot(
                    preferences,
                    List.of(new LibraryCategory(
                            "Seasonal",
                            LibraryCategoryScope.ANIME,
                            LibraryDisplayMode.GRID,
                            LibraryDisplayDensity.RELAXED,
                            LibrarySort.TITLE_DESCENDING,
                            LibraryCategoryUpdatePolicy.EXCLUDE)));
            configuration.save(snapshot);
            counter.check(new FileLibraryConfiguration(file).snapshot().equals(snapshot),
                    "library display and category configuration must survive restart");
            counter.check(noTemporaryFiles(directory),
                    "atomic library configuration save must not leave temporary files");
        } finally {
            deleteDirectory(directory);
        }
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
                            PublicationStatus.ONGOING,
                            Optional.of(URI.create("https://images.example/cover.jpg")),
                            List.of("Action", "Drama")))
                    .withOrigin(new LibraryOrigin("test.source", "remote-title-7"));
            counter.check(item.favorite(), "favourite state must be expressible");
            counter.check(item.progress().orElseThrow().completion().orElseThrow() > 0.68,
                    "progress must expose a normalized completion");
            counter.check(item.history().size() == 2, "history must retain chronological visits");
            counter.check(item.metadata().publicationStatus() == PublicationStatus.ONGOING,
                    "per-title publication metadata must be typed");
            counter.check(item.metadata().artwork().orElseThrow().getHost().equals("images.example")
                            && item.metadata().genres().equals(List.of("Action", "Drama")),
                    "title artwork and genres must be expressible");
            counter.check(item.origin().orElseThrow().sourceId().equals("test.source"),
                    "source origin must be expressible");
            FileLibraryCatalog catalog = new FileLibraryCatalog(file);
            catalog.save(item);
            LibraryItem hidden = LibraryItem.create("History only", MediaKind.MANGA)
                    .withOrigin(new LibraryOrigin("test.source", "history-only"))
                    .recordHistory(new LibraryHistoryEntry(
                            "chapter-2",
                            Instant.parse("2026-08-17T14:00:00Z"),
                            2L))
                    .withLibraryMembership(false);
            catalog.save(hidden);

            FileLibraryCatalog reloaded = new FileLibraryCatalog(file);
            counter.check(reloaded.find(item.id()).orElseThrow().equals(item),
                    "current file format must preserve every library field");
            counter.check(!reloaded.find(hidden.id()).orElseThrow().inLibrary()
                            && reloaded.find(hidden.id()).orElseThrow().history().size() == 1,
                    "current file format must preserve history-only indexed titles");
            counter.check(noTemporaryFiles(directory), "atomic save must not leave temporary files");
            counter.check(reloaded.remove(item.id()), "durable catalog must remove existing items");
            counter.check(reloaded.remove(hidden.id()), "hidden catalog items must remain removable");
            counter.check(new FileLibraryCatalog(file).snapshot().isEmpty(),
                    "removal must survive a catalog restart");
        } finally {
            deleteDirectory(directory);
        }
    }

    private static void migratesVersionOneLibraryConfiguration(Counter counter) throws IOException {
        Path directory = Files.createTempDirectory("anilib-library-configuration-migration");
        Path file = directory.resolve("library.configuration");
        try {
            writeVersionOneLibraryConfiguration(file);
            LibraryConfigurationSnapshot migrated = new FileLibraryConfiguration(file).snapshot();
            counter.check(migrated.categories().getFirst().scope() == LibraryCategoryScope.SHARED,
                    "version one categories must remain available to anime and manga after migration");
            counter.check(readVersion(file, CONFIGURATION_MAGIC) == CURRENT_CONFIGURATION_VERSION,
                    "opening a legacy library configuration must rewrite the current version");
            counter.check(noTemporaryFiles(directory),
                    "library configuration migration must not leave temporary files");
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
                    "opening a version one catalog must rewrite the current version");
        } finally {
            deleteDirectory(directory);
        }
    }

    private static void migratesVersionTwo(Counter counter) throws IOException {
        Path directory = Files.createTempDirectory("anilib-library-v2-migration");
        Path file = directory.resolve("library.anilib");
        LibraryItemId id = new LibraryItemId("version-two-item");
        try {
            writeVersionTwo(file, id);
            LibraryItem item = new FileLibraryCatalog(file).find(id).orElseThrow();
            counter.check(item.favorite(), "version two favourite state must survive migration");
            counter.check(item.origin().isEmpty(), "version two migration must initialize source origin");
            counter.check(readVersion(file) == CURRENT_VERSION,
                    "opening a version two catalog must rewrite the current version");
        } finally {
            deleteDirectory(directory);
        }
    }

    private static void migratesVersionThree(Counter counter) throws IOException {
        Path directory = Files.createTempDirectory("anilib-library-v3-migration");
        Path file = directory.resolve("library.anilib");
        LibraryItemId id = new LibraryItemId("version-three-item");
        try {
            writeVersionThree(file, id);
            LibraryItem item = new FileLibraryCatalog(file).find(id).orElseThrow();
            counter.check(item.origin().orElseThrow().sourceId().equals("legacy.source"),
                    "version three source origin must survive migration");
            counter.check(item.metadata().artwork().isEmpty()
                            && item.metadata().genres().isEmpty(),
                    "version three migration must initialize artwork and genres");
            counter.check(readVersion(file) == CURRENT_VERSION,
                    "opening a version three catalog must rewrite the current version");
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

    private static void writeVersionOneLibraryConfiguration(Path file) throws IOException {
        try (DataOutputStream output = new DataOutputStream(
                new BufferedOutputStream(Files.newOutputStream(file)))) {
            output.writeInt(CONFIGURATION_MAGIC);
            output.writeInt(1);
            output.writeUTF(LibraryDisplayMode.GRID.name());
            output.writeUTF(LibraryDisplayDensity.COMFORTABLE.name());
            output.writeUTF(LibrarySort.TITLE_ASCENDING.name());
            output.writeBoolean(false);
            output.writeInt(1);
            output.writeUTF("Legacy category");
            output.writeUTF(LibraryDisplayMode.LIST.name());
            output.writeUTF(LibraryDisplayDensity.COMPACT.name());
            output.writeUTF(LibrarySort.ADDED_NEWEST.name());
            output.writeUTF(LibraryCategoryUpdatePolicy.DEFAULT.name());
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

    private static void writeVersionTwo(Path file, LibraryItemId id) throws IOException {
        Instant addedAt = Instant.parse("2026-02-03T04:05:06.123456789Z");
        try (DataOutputStream output = new DataOutputStream(
                new BufferedOutputStream(Files.newOutputStream(file)))) {
            output.writeInt(MAGIC);
            output.writeInt(2);
            output.writeInt(1);
            output.writeUTF(id.value());
            output.writeUTF("Version two title");
            output.writeUTF(MediaKind.MANGA.name());
            output.writeLong(addedAt.getEpochSecond());
            output.writeInt(addedAt.getNano());
            output.writeInt(0);
            output.writeBoolean(true);
            output.writeBoolean(false);
            output.writeInt(0);
            output.writeUTF("Version two metadata");
            output.writeInt(0);
            output.writeInt(0);
            output.writeUTF(PublicationStatus.COMPLETED.name());
        }
    }

    private static void writeVersionThree(Path file, LibraryItemId id) throws IOException {
        Instant addedAt = Instant.parse("2026-03-04T05:06:07Z");
        try (DataOutputStream output = new DataOutputStream(
                new BufferedOutputStream(Files.newOutputStream(file)))) {
            output.writeInt(MAGIC);
            output.writeInt(3);
            output.writeInt(1);
            output.writeUTF(id.value());
            output.writeUTF("Version three title");
            output.writeUTF(MediaKind.MANGA.name());
            output.writeLong(addedAt.getEpochSecond());
            output.writeInt(addedAt.getNano());
            output.writeInt(0);
            output.writeBoolean(false);
            output.writeBoolean(false);
            output.writeInt(0);
            output.writeUTF("Version three metadata");
            output.writeInt(0);
            output.writeInt(0);
            output.writeUTF(PublicationStatus.ONGOING.name());
            output.writeBoolean(true);
            output.writeUTF("legacy.source");
            output.writeUTF("legacy-title");
        }
    }

    private static void checkEnrichedDefaults(Counter counter, LibraryItem item, String version) {
        counter.check(!item.favorite(), version + " migration must default favourite to false");
        counter.check(item.progress().isEmpty(), version + " migration must initialize progress");
        counter.check(item.history().isEmpty(), version + " migration must initialize history");
        counter.check(item.metadata().equals(LibraryTitleMetadata.empty()),
                version + " migration must initialize title metadata");
        counter.check(item.origin().isEmpty(), version + " migration must initialize source origin");
    }

    private static int readVersion(Path file) throws IOException {
        return readVersion(file, MAGIC);
    }

    private static int readVersion(Path file, int expectedMagic) throws IOException {
        try (DataInputStream input = new DataInputStream(Files.newInputStream(file))) {
            if (input.readInt() != expectedMagic) {
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
