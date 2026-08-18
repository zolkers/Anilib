package fr.vriege.anilib.feature.reader.runtime;

import fr.vriege.anilib.feature.reader.ReaderInteractionAction;
import fr.vriege.anilib.feature.reader.ReaderInteractionPreferenceStore;
import fr.vriege.anilib.feature.reader.ReaderInteractionPreferences;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class FileReaderInteractionPreferenceStore implements ReaderInteractionPreferenceStore {
    private final Path file;

    public FileReaderInteractionPreferenceStore(Path file) {
        this.file = Objects.requireNonNull(file, "file must not be null").toAbsolutePath().normalize();
    }

    @Override
    public synchronized ReaderInteractionPreferences snapshot() {
        ReaderInteractionPreferences defaults = ReaderInteractionPreferences.defaults();
        if (!Files.exists(file)) {
            return defaults;
        }
        Map<String, ReaderInteractionAction> values = new LinkedHashMap<>();
        try {
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                String[] columns = line.split("=", -1);
                if (columns.length != 2 || values.putIfAbsent(
                        columns[0],
                        ReaderInteractionAction.valueOf(columns[1])) != null) {
                    throw new IllegalStateException("Invalid reader interaction preference row");
                }
            }
            return new ReaderInteractionPreferences(
                    value(values, "leftTap", defaults.leftTap()),
                    value(values, "centerTap", defaults.centerTap()),
                    value(values, "rightTap", defaults.rightTap()),
                    value(values, "topTap", defaults.topTap()),
                    value(values, "bottomTap", defaults.bottomTap()),
                    value(values, "swipeLeft", defaults.swipeLeft()),
                    value(values, "swipeRight", defaults.swipeRight()),
                    value(values, "swipeUp", defaults.swipeUp()),
                    value(values, "swipeDown", defaults.swipeDown()),
                    value(values, "doubleTap", defaults.doubleTap()),
                    value(values, "longPress", defaults.longPress()));
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("Invalid reader interaction preference value", exception);
        } catch (IOException exception) {
            throw new UncheckedIOException("Unable to read reader interaction preferences", exception);
        }
    }

    @Override
    public synchronized void save(ReaderInteractionPreferences preferences) {
        ReaderInteractionPreferences value = Objects.requireNonNull(preferences, "preferences must not be null");
        write(List.of(
                row("leftTap", value.leftTap()),
                row("centerTap", value.centerTap()),
                row("rightTap", value.rightTap()),
                row("topTap", value.topTap()),
                row("bottomTap", value.bottomTap()),
                row("swipeLeft", value.swipeLeft()),
                row("swipeRight", value.swipeRight()),
                row("swipeUp", value.swipeUp()),
                row("swipeDown", value.swipeDown()),
                row("doubleTap", value.doubleTap()),
                row("longPress", value.longPress())));
    }

    private void write(List<String> lines) {
        Path parent = file.getParent();
        Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        try {
            Files.createDirectories(parent);
            Files.write(temporary, lines, StandardCharsets.UTF_8);
            moveAtomically(temporary, file);
        } catch (IOException exception) {
            throw new UncheckedIOException("Unable to write reader interaction preferences", exception);
        } finally {
            try {
                Files.deleteIfExists(temporary);
            } catch (IOException ignored) {
                // The primary operation reports the actionable error.
            }
        }
    }

    private static ReaderInteractionAction value(
            Map<String, ReaderInteractionAction> values,
            String key,
            ReaderInteractionAction fallback) {
        return values.getOrDefault(key, fallback);
    }

    private static String row(String key, ReaderInteractionAction value) {
        return key + "=" + value.name();
    }

    private static void moveAtomically(Path source, Path destination) throws IOException {
        try {
            Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
