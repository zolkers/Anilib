package fr.vriege.anilib.feature.library.runtime;

import fr.vriege.anilib.feature.library.LibraryCategory;
import fr.vriege.anilib.feature.library.LibraryCategoryUpdatePolicy;
import fr.vriege.anilib.feature.library.LibraryCategoryScope;
import fr.vriege.anilib.feature.library.LibraryConfiguration;
import fr.vriege.anilib.feature.library.LibraryConfigurationSnapshot;
import fr.vriege.anilib.feature.library.LibraryDisplayDensity;
import fr.vriege.anilib.feature.library.LibraryDisplayMode;
import fr.vriege.anilib.feature.library.LibraryDisplayPreferences;
import fr.vriege.anilib.feature.library.LibrarySort;
import fr.vriege.anilib.feature.library.LibraryStorageException;

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
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class FileLibraryConfiguration implements LibraryConfiguration {
    private static final int MAGIC = 0x414E4C43;
    private static final int VERSION = 2;
    private static final int FIRST_SUPPORTED_VERSION = 1;
    private static final int MAXIMUM_CATEGORIES = 10_000;

    private final Path file;
    private LibraryConfigurationSnapshot snapshot;

    public FileLibraryConfiguration(Path file) {
        this.file = Objects.requireNonNull(file, "file must not be null").toAbsolutePath().normalize();
        LoadedConfiguration loaded = load();
        snapshot = loaded.snapshot();
        if (loaded.version() < VERSION) {
            save(snapshot);
        }
    }

    @Override
    public synchronized LibraryConfigurationSnapshot snapshot() {
        return snapshot;
    }

    @Override
    public synchronized void save(LibraryConfigurationSnapshot replacement) {
        Objects.requireNonNull(replacement, "replacement must not be null");
        Path parent = file.getParent();
        try {
            Files.createDirectories(parent);
            Path temporary = Files.createTempFile(parent, file.getFileName().toString(), ".tmp");
            try {
                write(temporary, replacement);
                moveAtomically(temporary);
                snapshot = replacement;
            } finally {
                Files.deleteIfExists(temporary);
            }
        } catch (IOException exception) {
            throw failure("write", exception);
        }
    }

    private LoadedConfiguration load() {
        if (!Files.exists(file)) {
            return new LoadedConfiguration(VERSION, LibraryConfigurationSnapshot.defaults());
        }
        try (DataInputStream input = new DataInputStream(
                new BufferedInputStream(Files.newInputStream(file)))) {
            if (input.readInt() != MAGIC) {
                throw new IOException("Invalid Anilib library configuration signature");
            }
            int version = input.readInt();
            if (version < FIRST_SUPPORTED_VERSION || version > VERSION) {
                throw new IOException("Unsupported Anilib library configuration version: " + version);
            }
            LibraryDisplayPreferences preferences = new LibraryDisplayPreferences(
                    LibraryDisplayMode.valueOf(input.readUTF()),
                    LibraryDisplayDensity.valueOf(input.readUTF()),
                    LibrarySort.valueOf(input.readUTF()),
                    input.readBoolean() ? Optional.of(input.readUTF()) : Optional.empty());
            int categoryCount = input.readInt();
            if (categoryCount < 0 || categoryCount > MAXIMUM_CATEGORIES) {
                throw new IOException("Invalid library category count: " + categoryCount);
            }
            List<LibraryCategory> categories = new ArrayList<>(categoryCount);
            for (int index = 0; index < categoryCount; index++) {
                categories.add(new LibraryCategory(
                        input.readUTF(),
                        version >= 2
                                ? LibraryCategoryScope.valueOf(input.readUTF())
                                : LibraryCategoryScope.SHARED,
                        LibraryDisplayMode.valueOf(input.readUTF()),
                        LibraryDisplayDensity.valueOf(input.readUTF()),
                        LibrarySort.valueOf(input.readUTF()),
                        LibraryCategoryUpdatePolicy.valueOf(input.readUTF())));
            }
            if (input.read() != -1) {
                throw new IOException("Unexpected trailing library configuration data");
            }
            return new LoadedConfiguration(
                    version,
                    new LibraryConfigurationSnapshot(preferences, categories));
        } catch (EOFException exception) {
            throw failure("read truncated", exception);
        } catch (IOException | IllegalArgumentException exception) {
            throw failure("read", exception);
        }
    }

    private static void write(Path destination, LibraryConfigurationSnapshot value) throws IOException {
        if (value.categories().size() > MAXIMUM_CATEGORIES) {
            throw new IOException("Too many library categories: " + value.categories().size());
        }
        try (FileChannel channel = FileChannel.open(
                destination,
                StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING);
             DataOutputStream output = new DataOutputStream(
                     new BufferedOutputStream(Channels.newOutputStream(channel)))) {
            output.writeInt(MAGIC);
            output.writeInt(VERSION);
            LibraryDisplayPreferences preferences = value.displayPreferences();
            output.writeUTF(preferences.mode().name());
            output.writeUTF(preferences.density().name());
            output.writeUTF(preferences.sort().name());
            output.writeBoolean(preferences.defaultCategory().isPresent());
            if (preferences.defaultCategory().isPresent()) {
                output.writeUTF(preferences.defaultCategory().orElseThrow());
            }
            output.writeInt(value.categories().size());
            for (LibraryCategory category : value.categories()) {
                output.writeUTF(category.name());
                output.writeUTF(category.scope().name());
                output.writeUTF(category.displayMode().name());
                output.writeUTF(category.density().name());
                output.writeUTF(category.sort().name());
                output.writeUTF(category.updatePolicy().name());
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

    private static LibraryStorageException failure(String operation, Exception cause) {
        return new LibraryStorageException(
                "Unable to " + operation + " the Anilib library configuration",
                cause);
    }

    private record LoadedConfiguration(int version, LibraryConfigurationSnapshot snapshot) {
    }
}
