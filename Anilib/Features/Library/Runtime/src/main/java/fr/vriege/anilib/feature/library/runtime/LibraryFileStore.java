package fr.vriege.anilib.feature.library.runtime;

import fr.vriege.anilib.feature.library.LibraryItem;
import fr.vriege.anilib.feature.library.LibraryItemId;
import fr.vriege.anilib.feature.library.LibraryOrigin;
import fr.vriege.anilib.feature.library.LibraryHistoryEntry;
import fr.vriege.anilib.feature.library.LibraryProgress;
import fr.vriege.anilib.feature.library.LibraryTitleMetadata;
import fr.vriege.anilib.feature.library.MediaKind;
import fr.vriege.anilib.feature.library.PublicationStatus;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Objects;
import java.util.Set;
import java.io.InputStream;
import java.net.URI;

final class LibraryFileStore {
    static final int MAGIC = 0x414E494C;
    static final int CURRENT_VERSION = 5;

    private static final int MAX_ITEMS = 1_000_000;
    private static final int MAX_CATEGORIES_PER_ITEM = 1_000;
    private static final int MAX_HISTORY_ENTRIES_PER_ITEM = 100_000;
    private static final int MAX_PEOPLE_PER_ITEM = 10_000;
    private final Path file;

    LibraryFileStore(Path file) {
        this.file = file.toAbsolutePath().normalize();
    }

    LoadResult load() throws IOException {
        if (!Files.exists(file)) {
            return new LoadResult(List.of(), false, CURRENT_VERSION);
        }
        return read(new BufferedInputStream(Files.newInputStream(file)));
    }

    static LoadResult decode(byte[] payload) throws IOException {
        Objects.requireNonNull(payload, "payload must not be null");
        return read(new ByteArrayInputStream(payload));
    }

    static byte[] encode(Collection<LibraryItem> items) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(new BufferedOutputStream(bytes))) {
            writeCurrent(output, items);
        }
        return bytes.toByteArray();
    }

    private static LoadResult read(InputStream source) throws IOException {
        try (DataInputStream input = new DataInputStream(source)) {
            if (input.readInt() != MAGIC) {
                throw new IOException("Invalid Anilib library file signature");
            }
            int version = input.readInt();
            List<LibraryItem> items = switch (version) {
                case 0 -> readVersionZero(input);
                case 1 -> readVersionOne(input);
                case 2 -> readVersionTwo(input);
                case 3 -> readVersionThree(input);
                case 4 -> readVersionFour(input);
                case CURRENT_VERSION -> readVersionFive(input);
                default -> throw new IOException("Unsupported Anilib library version: " + version);
            };
            if (input.read() != -1) {
                throw new IOException("Unexpected trailing data in Anilib library file");
            }
            return new LoadResult(items, version < CURRENT_VERSION, version);
        } catch (EOFException exception) {
            throw new IOException("Truncated Anilib library file", exception);
        } catch (IllegalArgumentException | DateTimeException exception) {
            throw new IOException("Invalid value in Anilib library file", exception);
        }
    }

    void save(Collection<LibraryItem> items) throws IOException {
        Path parent = file.getParent();
        Files.createDirectories(parent);
        Path temporary = Files.createTempFile(parent, file.getFileName().toString(), ".tmp");
        try {
            writeCurrent(temporary, items);
            moveAtomically(temporary);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private void writeCurrent(Path destination, Collection<LibraryItem> items) throws IOException {
        List<LibraryItem> ordered = items.stream()
                .sorted(Comparator.comparing(LibraryItem::id))
                .toList();
        try (FileChannel channel = FileChannel.open(
                destination,
                StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING);
             DataOutputStream output = new DataOutputStream(
                     new BufferedOutputStream(Channels.newOutputStream(channel)))) {
            writeCurrent(output, ordered);
            output.flush();
            channel.force(true);
        }
    }

    private static void writeCurrent(
            DataOutputStream output,
            Collection<LibraryItem> items) throws IOException {
        List<LibraryItem> ordered = items.stream()
                .sorted(Comparator.comparing(LibraryItem::id))
                .toList();
        output.writeInt(MAGIC);
        output.writeInt(CURRENT_VERSION);
        output.writeInt(ordered.size());
        for (LibraryItem item : ordered) {
            writeVersionFiveItem(output, item);
        }
    }

    private void moveAtomically(Path temporary) throws IOException {
        try {
            Files.move(
                    temporary,
                    file,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            throw new IOException("Atomic file replacement is not supported for " + file, exception);
        }
    }

    private static List<LibraryItem> readVersionZero(DataInputStream input) throws IOException {
        int itemCount = readCount(input, MAX_ITEMS, "item");
        List<LibraryItem> items = new ArrayList<>(itemCount);
        Set<LibraryItemId> identifiers = new HashSet<>();
        for (int index = 0; index < itemCount; index++) {
            LibraryItem item = new LibraryItem(
                    new LibraryItemId(input.readUTF()),
                    input.readUTF(),
                    MediaKind.valueOf(input.readUTF()),
                    Instant.ofEpochMilli(input.readLong()),
                    Set.of());
            addUnique(items, identifiers, item);
        }
        return List.copyOf(items);
    }

    private static List<LibraryItem> readVersionOne(DataInputStream input) throws IOException {
        int itemCount = readCount(input, MAX_ITEMS, "item");
        List<LibraryItem> items = new ArrayList<>(itemCount);
        Set<LibraryItemId> identifiers = new HashSet<>();
        for (int index = 0; index < itemCount; index++) {
            LibraryItemId id = new LibraryItemId(input.readUTF());
            String title = input.readUTF();
            MediaKind kind = MediaKind.valueOf(input.readUTF());
            Instant addedAt = Instant.ofEpochSecond(input.readLong(), input.readInt());
            int categoryCount = readCount(input, MAX_CATEGORIES_PER_ITEM, "category");
            Set<String> categories = new HashSet<>();
            for (int categoryIndex = 0; categoryIndex < categoryCount; categoryIndex++) {
                if (!categories.add(input.readUTF())) {
                    throw new IOException("Duplicate category in Anilib library file");
                }
            }
            addUnique(items, identifiers, new LibraryItem(id, title, kind, addedAt, categories));
        }
        return List.copyOf(items);
    }

    private static List<LibraryItem> readVersionTwo(DataInputStream input) throws IOException {
        int itemCount = readCount(input, MAX_ITEMS, "item");
        List<LibraryItem> items = new ArrayList<>(itemCount);
        Set<LibraryItemId> identifiers = new HashSet<>();
        for (int index = 0; index < itemCount; index++) {
            LibraryItemId id = new LibraryItemId(input.readUTF());
            String title = input.readUTF();
            MediaKind kind = MediaKind.valueOf(input.readUTF());
            Instant addedAt = readInstant(input);
            Set<String> categories = readUniqueStrings(
                    input,
                    MAX_CATEGORIES_PER_ITEM,
                    "category");
            boolean favorite = input.readBoolean();
            Optional<LibraryProgress> progress = readProgress(input);
            List<LibraryHistoryEntry> history = readHistory(input);
            LibraryTitleMetadata metadata = readMetadata(input);
            addUnique(items, identifiers, new LibraryItem(
                    id,
                    title,
                    kind,
                    addedAt,
                    categories,
                    favorite,
                    progress,
                    history,
                    metadata));
        }
        return List.copyOf(items);
    }

    private static List<LibraryItem> readVersionThree(DataInputStream input) throws IOException {
        int itemCount = readCount(input, MAX_ITEMS, "item");
        List<LibraryItem> items = new ArrayList<>(itemCount);
        Set<LibraryItemId> identifiers = new HashSet<>();
        for (int index = 0; index < itemCount; index++) {
            LibraryItemId id = new LibraryItemId(input.readUTF());
            String title = input.readUTF();
            MediaKind kind = MediaKind.valueOf(input.readUTF());
            Instant addedAt = readInstant(input);
            Set<String> categories = readUniqueStrings(
                    input,
                    MAX_CATEGORIES_PER_ITEM,
                    "category");
            boolean favorite = input.readBoolean();
            Optional<LibraryProgress> progress = readProgress(input);
            List<LibraryHistoryEntry> history = readHistory(input);
            LibraryTitleMetadata metadata = readMetadata(input);
            Optional<LibraryOrigin> origin = readOrigin(input);
            addUnique(items, identifiers, new LibraryItem(
                    id,
                    title,
                    kind,
                    addedAt,
                    categories,
                    favorite,
                    progress,
                    history,
                    metadata,
                    origin));
        }
        return List.copyOf(items);
    }

    private static List<LibraryItem> readVersionFour(DataInputStream input) throws IOException {
        int itemCount = readCount(input, MAX_ITEMS, "item");
        List<LibraryItem> items = new ArrayList<>(itemCount);
        Set<LibraryItemId> identifiers = new HashSet<>();
        for (int index = 0; index < itemCount; index++) {
            LibraryItemId id = new LibraryItemId(input.readUTF());
            String title = input.readUTF();
            MediaKind kind = MediaKind.valueOf(input.readUTF());
            Instant addedAt = readInstant(input);
            Set<String> categories = readUniqueStrings(
                    input,
                    MAX_CATEGORIES_PER_ITEM,
                    "category");
            boolean favorite = input.readBoolean();
            Optional<LibraryProgress> progress = readProgress(input);
            List<LibraryHistoryEntry> history = readHistory(input);
            LibraryTitleMetadata legacyMetadata = readMetadata(input);
            Optional<LibraryOrigin> origin = readOrigin(input);
            Optional<URI> artwork = input.readBoolean()
                    ? Optional.of(URI.create(input.readUTF()))
                    : Optional.empty();
            List<String> genres = readStrings(input, MAX_PEOPLE_PER_ITEM, "genre");
            LibraryTitleMetadata metadata = new LibraryTitleMetadata(
                    legacyMetadata.description(),
                    legacyMetadata.authors(),
                    legacyMetadata.artists(),
                    legacyMetadata.publicationStatus(),
                    artwork,
                    genres);
            addUnique(items, identifiers, new LibraryItem(
                    id,
                    title,
                    kind,
                    addedAt,
                    categories,
                    favorite,
                    progress,
                    history,
                    metadata,
                    origin));
        }
        return List.copyOf(items);
    }

    private static List<LibraryItem> readVersionFive(DataInputStream input) throws IOException {
        int itemCount = readCount(input, MAX_ITEMS, "item");
        List<LibraryItem> items = new ArrayList<>(itemCount);
        Set<LibraryItemId> identifiers = new HashSet<>();
        for (int index = 0; index < itemCount; index++) {
            LibraryItemId id = new LibraryItemId(input.readUTF());
            String title = input.readUTF();
            MediaKind kind = MediaKind.valueOf(input.readUTF());
            Instant addedAt = readInstant(input);
            Set<String> categories = readUniqueStrings(input, MAX_CATEGORIES_PER_ITEM, "category");
            boolean favorite = input.readBoolean();
            Optional<LibraryProgress> progress = readProgress(input);
            List<LibraryHistoryEntry> history = readHistory(input);
            LibraryTitleMetadata legacyMetadata = readMetadata(input);
            Optional<LibraryOrigin> origin = readOrigin(input);
            Optional<URI> artwork = input.readBoolean()
                    ? Optional.of(URI.create(input.readUTF()))
                    : Optional.empty();
            List<String> genres = readStrings(input, MAX_PEOPLE_PER_ITEM, "genre");
            boolean inLibrary = input.readBoolean();
            LibraryTitleMetadata metadata = new LibraryTitleMetadata(
                    legacyMetadata.description(),
                    legacyMetadata.authors(),
                    legacyMetadata.artists(),
                    legacyMetadata.publicationStatus(),
                    artwork,
                    genres);
            addUnique(items, identifiers, new LibraryItem(
                    id,
                    title,
                    kind,
                    addedAt,
                    categories,
                    favorite,
                    progress,
                    history,
                    metadata,
                    origin,
                    inLibrary));
        }
        return List.copyOf(items);
    }

    private static void addUnique(
            List<LibraryItem> items,
            Set<LibraryItemId> identifiers,
            LibraryItem item) throws IOException {
        if (!identifiers.add(item.id())) {
            throw new IOException("Duplicate library item id: " + item.id());
        }
        items.add(item);
    }

    private static int readCount(DataInputStream input, int maximum, String label) throws IOException {
        int count = input.readInt();
        if (count < 0 || count > maximum) {
            throw new IOException("Invalid " + label + " count: " + count);
        }
        return count;
    }

    private static void writeVersionTwoItem(DataOutputStream output, LibraryItem item) throws IOException {
        output.writeUTF(item.id().value());
        output.writeUTF(item.title());
        output.writeUTF(item.kind().name());
        writeInstant(output, item.addedAt());
        writeStrings(
                output,
                item.categories().stream().sorted().toList(),
                MAX_CATEGORIES_PER_ITEM,
                "category");
        output.writeBoolean(item.favorite());
        writeProgress(output, item.progress());
        writeHistory(output, item.history());
        writeMetadata(output, item.metadata());
    }

    private static void writeVersionThreeItem(DataOutputStream output, LibraryItem item) throws IOException {
        writeVersionTwoItem(output, item);
        output.writeBoolean(item.origin().isPresent());
        if (item.origin().isPresent()) {
            LibraryOrigin origin = item.origin().orElseThrow();
            output.writeUTF(origin.sourceId());
            output.writeUTF(origin.sourceItemKey());
        }
    }

    private static void writeVersionFourItem(DataOutputStream output, LibraryItem item) throws IOException {
        writeVersionThreeItem(output, item);
        output.writeBoolean(item.metadata().artwork().isPresent());
        if (item.metadata().artwork().isPresent()) {
            output.writeUTF(item.metadata().artwork().orElseThrow().toASCIIString());
        }
        writeStrings(output, item.metadata().genres(), MAX_PEOPLE_PER_ITEM, "genre");
    }

    private static void writeVersionFiveItem(DataOutputStream output, LibraryItem item) throws IOException {
        writeVersionFourItem(output, item);
        output.writeBoolean(item.inLibrary());
    }

    private static Optional<LibraryOrigin> readOrigin(DataInputStream input) throws IOException {
        if (!input.readBoolean()) {
            return Optional.empty();
        }
        return Optional.of(new LibraryOrigin(input.readUTF(), input.readUTF()));
    }

    private static Optional<LibraryProgress> readProgress(DataInputStream input) throws IOException {
        if (!input.readBoolean()) {
            return Optional.empty();
        }
        return Optional.of(new LibraryProgress(
                input.readUTF(),
                input.readLong(),
                input.readLong(),
                readInstant(input)));
    }

    private static List<LibraryHistoryEntry> readHistory(DataInputStream input) throws IOException {
        int count = readCount(input, MAX_HISTORY_ENTRIES_PER_ITEM, "history entry");
        List<LibraryHistoryEntry> history = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            history.add(new LibraryHistoryEntry(input.readUTF(), readInstant(input), input.readLong()));
        }
        return List.copyOf(history);
    }

    private static LibraryTitleMetadata readMetadata(DataInputStream input) throws IOException {
        String description = input.readUTF();
        List<String> authors = readStrings(input, MAX_PEOPLE_PER_ITEM, "author");
        List<String> artists = readStrings(input, MAX_PEOPLE_PER_ITEM, "artist");
        PublicationStatus status = PublicationStatus.valueOf(input.readUTF());
        return new LibraryTitleMetadata(description, authors, artists, status);
    }

    private static Set<String> readUniqueStrings(
            DataInputStream input,
            int maximum,
            String label) throws IOException {
        List<String> values = readStrings(input, maximum, label);
        Set<String> unique = new HashSet<>(values);
        if (unique.size() != values.size()) {
            throw new IOException("Duplicate " + label + " in Anilib library file");
        }
        return Set.copyOf(unique);
    }

    private static List<String> readStrings(
            DataInputStream input,
            int maximum,
            String label) throws IOException {
        int count = readCount(input, maximum, label);
        List<String> values = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            values.add(input.readUTF());
        }
        return List.copyOf(values);
    }

    private static Instant readInstant(DataInputStream input) throws IOException {
        return Instant.ofEpochSecond(input.readLong(), input.readInt());
    }

    private static void writeProgress(
            DataOutputStream output,
            Optional<LibraryProgress> progress) throws IOException {
        output.writeBoolean(progress.isPresent());
        if (progress.isEmpty()) {
            return;
        }
        LibraryProgress value = progress.orElseThrow();
        output.writeUTF(value.contentId());
        output.writeLong(value.position());
        output.writeLong(value.extent());
        writeInstant(output, value.updatedAt());
    }

    private static void writeHistory(
            DataOutputStream output,
            List<LibraryHistoryEntry> history) throws IOException {
        validateCount(history.size(), MAX_HISTORY_ENTRIES_PER_ITEM, "history entry");
        output.writeInt(history.size());
        for (LibraryHistoryEntry entry : history) {
            output.writeUTF(entry.contentId());
            writeInstant(output, entry.openedAt());
            output.writeLong(entry.position());
        }
    }

    private static void writeMetadata(
            DataOutputStream output,
            LibraryTitleMetadata metadata) throws IOException {
        output.writeUTF(metadata.description());
        writeStrings(output, metadata.authors(), MAX_PEOPLE_PER_ITEM, "author");
        writeStrings(output, metadata.artists(), MAX_PEOPLE_PER_ITEM, "artist");
        output.writeUTF(metadata.publicationStatus().name());
    }

    private static void writeStrings(
            DataOutputStream output,
            List<String> values,
            int maximum,
            String label) throws IOException {
        validateCount(values.size(), maximum, label);
        output.writeInt(values.size());
        for (String value : values) {
            output.writeUTF(value);
        }
    }

    private static void writeInstant(DataOutputStream output, Instant instant) throws IOException {
        output.writeLong(instant.getEpochSecond());
        output.writeInt(instant.getNano());
    }

    private static void validateCount(int count, int maximum, String label) throws IOException {
        if (count > maximum) {
            throw new IOException("Too many " + label + " values: " + count);
        }
    }

    record LoadResult(List<LibraryItem> items, boolean migrationRequired, int version) {
        LoadResult {
            items = List.copyOf(items);
        }
    }
}
