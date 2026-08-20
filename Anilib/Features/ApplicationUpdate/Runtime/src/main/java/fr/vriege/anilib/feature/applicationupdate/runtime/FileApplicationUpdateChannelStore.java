package fr.vriege.anilib.feature.applicationupdate.runtime;

import fr.vriege.anilib.feature.applicationupdate.ApplicationUpdateChannel;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.nio.file.AtomicMoveNotSupportedException;

public final class FileApplicationUpdateChannelStore {
    private final Path file;

    public FileApplicationUpdateChannelStore(Path file) {
        this.file = Objects.requireNonNull(file, "file must not be null").toAbsolutePath().normalize();
    }

    public synchronized ApplicationUpdateChannel load() {
        if (!Files.isRegularFile(file)) {
            return ApplicationUpdateChannel.STABLE;
        }
        try {
            return ApplicationUpdateChannel.valueOf(Files.readString(file, StandardCharsets.UTF_8).strip());
        } catch (IOException | IllegalArgumentException exception) {
            throw new IllegalStateException("Unable to read the application update channel", exception);
        }
    }

    public synchronized void save(ApplicationUpdateChannel channel) {
        Objects.requireNonNull(channel, "channel must not be null");
        Path parent = file.getParent();
        Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        try {
            Files.createDirectories(parent);
            Files.writeString(temporary, channel.name() + System.lineSeparator(), StandardCharsets.UTF_8);
            try {
                Files.move(
                        temporary,
                        file,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to save the application update channel", exception);
        } finally {
            try {
                Files.deleteIfExists(temporary);
            } catch (IOException ignored) {
                // The next save replaces the same bounded temporary file.
            }
        }
    }
}
