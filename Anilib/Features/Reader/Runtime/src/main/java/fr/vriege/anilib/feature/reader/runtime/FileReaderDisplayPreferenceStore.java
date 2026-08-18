package fr.vriege.anilib.feature.reader.runtime;

import fr.vriege.anilib.feature.reader.ReaderDisplayPreferenceStore;
import fr.vriege.anilib.feature.reader.ReaderDisplayPreferences;
import fr.vriege.anilib.feature.reader.ReaderRotation;
import fr.vriege.anilib.feature.reader.ReaderScaleMode;

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

public final class FileReaderDisplayPreferenceStore implements ReaderDisplayPreferenceStore {
    private final Path file;

    public FileReaderDisplayPreferenceStore(Path file) {
        this.file = Objects.requireNonNull(file, "file must not be null").toAbsolutePath().normalize();
    }

    @Override
    public synchronized ReaderDisplayPreferences snapshot() {
        ReaderDisplayPreferences defaults = ReaderDisplayPreferences.defaults();
        if (!Files.exists(file)) {
            return defaults;
        }
        Map<String, String> values = readRows();
        try {
            return new ReaderDisplayPreferences(
                    ReaderScaleMode.valueOf(values.getOrDefault("scaleMode", defaults.scaleMode().name())),
                    booleanValue(values, "cropBorders", defaults.cropBorders()),
                    booleanValue(values, "splitPages", defaults.splitPages()),
                    ReaderRotation.valueOf(values.getOrDefault("rotation", defaults.rotation().name())),
                    booleanValue(values, "dualPage", defaults.dualPage()),
                    integerValue(values, "webtoonSpacingDp", defaults.webtoonSpacingDp()));
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("Invalid reader display preference value", exception);
        }
    }

    @Override
    public synchronized void save(ReaderDisplayPreferences preferences) {
        ReaderDisplayPreferences value = Objects.requireNonNull(preferences, "preferences must not be null");
        write(List.of(
                "scaleMode=" + value.scaleMode().name(),
                "cropBorders=" + value.cropBorders(),
                "splitPages=" + value.splitPages(),
                "rotation=" + value.rotation().name(),
                "dualPage=" + value.dualPage(),
                "webtoonSpacingDp=" + value.webtoonSpacingDp()));
    }

    private Map<String, String> readRows() {
        Map<String, String> values = new LinkedHashMap<>();
        try {
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                String[] columns = line.split("=", -1);
                if (columns.length != 2 || values.putIfAbsent(columns[0], columns[1]) != null) {
                    throw new IllegalStateException("Invalid reader display preference row");
                }
            }
            return values;
        } catch (IOException exception) {
            throw new UncheckedIOException("Unable to read reader display preferences", exception);
        }
    }

    private void write(List<String> lines) {
        Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        try {
            Files.createDirectories(file.getParent());
            Files.write(temporary, lines, StandardCharsets.UTF_8);
            moveAtomically(temporary, file);
        } catch (IOException exception) {
            throw new UncheckedIOException("Unable to write reader display preferences", exception);
        } finally {
            try {
                Files.deleteIfExists(temporary);
            } catch (IOException ignored) {
                // The primary operation reports the actionable error.
            }
        }
    }

    private static boolean booleanValue(Map<String, String> values, String key, boolean fallback) {
        String value = values.get(key);
        if (value == null) {
            return fallback;
        }
        if (!value.equals("true") && !value.equals("false")) {
            throw new IllegalArgumentException("Invalid boolean value for " + key);
        }
        return Boolean.parseBoolean(value);
    }

    private static int integerValue(Map<String, String> values, String key, int fallback) {
        String value = values.get(key);
        return value == null ? fallback : Integer.parseInt(value);
    }

    private static void moveAtomically(Path source, Path destination) throws IOException {
        try {
            Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
