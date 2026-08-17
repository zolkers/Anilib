package fr.vriege.anilib.feature.library.runtime;

import fr.vriege.anilib.feature.library.LibraryItem;
import fr.vriege.anilib.feature.library.LibraryItemId;
import fr.vriege.anilib.feature.library.MediaKind;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
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
import java.util.Set;

/** Owns the versioned on-disk format and atomic replacement protocol. */
final class LibraryFileStore {
    static final int MAGIC = 0x414E494C;
    static final int CURRENT_VERSION = 1;

    private static final int MAX_ITEMS = 1_000_000;
    private static final int MAX_CATEGORIES_PER_ITEM = 1_000;
    private final Path file;

    LibraryFileStore(Path file) {
        this.file = file.toAbsolutePath().normalize();
    }

    LoadResult load() throws IOException {
        if (!Files.exists(file)) {
            return new LoadResult(List.of(), false);
        }
        try (DataInputStream input = new DataInputStream(
                new BufferedInputStream(Files.newInputStream(file)))) {
            if (input.readInt() != MAGIC) {
                throw new IOException("Invalid Anilib library file signature");
            }
            int version = input.readInt();
            List<LibraryItem> items = switch (version) {
                case 0 -> readVersionZero(input);
                case CURRENT_VERSION -> readVersionOne(input);
                default -> throw new IOException("Unsupported Anilib library version: " + version);
            };
            if (input.read() != -1) {
                throw new IOException("Unexpected trailing data in Anilib library file");
            }
            return new LoadResult(items, version < CURRENT_VERSION);
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
            output.writeInt(MAGIC);
            output.writeInt(CURRENT_VERSION);
            output.writeInt(ordered.size());
            for (LibraryItem item : ordered) {
                writeVersionOneItem(output, item);
            }
            output.flush();
            channel.force(true);
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

    private static void writeVersionOneItem(DataOutputStream output, LibraryItem item) throws IOException {
        output.writeUTF(item.id().value());
        output.writeUTF(item.title());
        output.writeUTF(item.kind().name());
        output.writeLong(item.addedAt().getEpochSecond());
        output.writeInt(item.addedAt().getNano());
        List<String> categories = item.categories().stream().sorted().toList();
        output.writeInt(categories.size());
        for (String category : categories) {
            output.writeUTF(category);
        }
    }

    record LoadResult(List<LibraryItem> items, boolean migrationRequired) {
        LoadResult {
            items = List.copyOf(items);
        }
    }
}
