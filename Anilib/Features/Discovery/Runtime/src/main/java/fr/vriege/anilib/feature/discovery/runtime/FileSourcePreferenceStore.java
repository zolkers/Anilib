package fr.vriege.anilib.feature.discovery.runtime;

import fr.vriege.anilib.feature.source.SourceId;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Objects;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;

public final class FileSourcePreferenceStore {
    private final Path file;
    private final Properties values = new Properties();

    public FileSourcePreferenceStore(Path file) {
        this.file = Objects.requireNonNull(file, "file must not be null").toAbsolutePath().normalize();
        load();
    }

    public synchronized String get(SourceId sourceId, String preferenceId, String defaultValue) {
        return values.getProperty(key(sourceId, preferenceId), defaultValue);
    }

    public synchronized void set(SourceId sourceId, String preferenceId, String value) {
        Properties next = copy(values);
        next.setProperty(key(sourceId, preferenceId), Objects.requireNonNull(value, "value must not be null"));
        persistAndReplace(next);
    }

    public synchronized Map<String, String> snapshot() {
        Map<String, String> snapshot = new TreeMap<>();
        values.forEach((key, value) -> snapshot.put(key.toString(), value.toString()));
        return Map.copyOf(snapshot);
    }

    public synchronized void replaceAll(Map<String, String> replacement) {
        Objects.requireNonNull(replacement, "replacement must not be null");
        Properties next = new Properties();
        replacement.forEach((key, value) -> {
            if (Objects.requireNonNull(key, "preference key must not be null").isBlank()) {
                throw new IllegalArgumentException("preference key must not be blank");
            }
            next.setProperty(key, Objects.requireNonNull(value, "preference value must not be null"));
        });
        persistAndReplace(next);
    }

    private void load() {
        if (!Files.exists(file)) {
            return;
        }
        if (!Files.isRegularFile(file) || Files.isSymbolicLink(file)) {
            throw new IllegalStateException("Source preference file must be a regular file");
        }
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            values.load(reader);
        } catch (IOException exception) {
            throw failure("load source preferences", exception);
        }
    }

    private void persistAndReplace(Properties replacement) {
        Path parent = file.getParent();
        Path temporary = null;
        try {
            Files.createDirectories(parent);
            temporary = Files.createTempFile(parent, ".source-preferences-", ".tmp");
            try (FileChannel channel = FileChannel.open(
                    temporary,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING);
                 BufferedWriter writer = new BufferedWriter(
                         Channels.newWriter(channel, StandardCharsets.UTF_8))) {
                replacement.store(writer, "Anilib source preferences");
                writer.flush();
                channel.force(true);
            }
            moveAtomically(temporary, file);
        } catch (IOException exception) {
            throw failure("store source preferences", exception);
        } finally {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException ignored) {
                    // The primary operation reports the actionable error.
                }
            }
        }
        values.clear();
        values.putAll(replacement);
    }

    private static Properties copy(Properties source) {
        Properties copy = new Properties();
        copy.putAll(source);
        return copy;
    }

    private static void moveAtomically(Path source, Path destination) throws IOException {
        try {
            Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            throw new IOException("Atomic preference replacement is unavailable", exception);
        }
    }

    private static String key(SourceId sourceId, String preferenceId) {
        Objects.requireNonNull(sourceId, "sourceId must not be null");
        String id = Objects.requireNonNull(preferenceId, "preferenceId must not be null");
        if (id.isBlank()) {
            throw new IllegalArgumentException("preferenceId must not be blank");
        }
        return sourceId + "." + id;
    }

    private static IllegalStateException failure(String operation, IOException cause) {
        return new IllegalStateException("Unable to " + operation, cause);
    }
}
