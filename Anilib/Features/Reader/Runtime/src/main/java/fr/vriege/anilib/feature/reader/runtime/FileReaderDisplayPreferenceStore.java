package fr.vriege.anilib.feature.reader.runtime;

import fr.vriege.anilib.feature.library.LibraryItemId;
import fr.vriege.anilib.feature.reader.ReaderColorFilter;
import fr.vriege.anilib.feature.reader.ReaderDisplayPreferenceStore;
import fr.vriege.anilib.feature.reader.ReaderDisplayPreferences;
import fr.vriege.anilib.feature.reader.ReaderOrientationPolicy;
import fr.vriege.anilib.feature.reader.ReaderPageTransition;
import fr.vriege.anilib.feature.reader.ReaderRotation;
import fr.vriege.anilib.feature.reader.ReaderScaleMode;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class FileReaderDisplayPreferenceStore implements ReaderDisplayPreferenceStore {
    private final Path file;

    public FileReaderDisplayPreferenceStore(Path file) {
        this.file = Objects.requireNonNull(file, "file must not be null").toAbsolutePath().normalize();
    }

    @Override
    public synchronized ReaderDisplayPreferences snapshot() {
        return preferences(readRows(), "", ReaderDisplayPreferences.defaults());
    }

    @Override
    public synchronized ReaderDisplayPreferences snapshot(LibraryItemId libraryItemId) {
        Objects.requireNonNull(libraryItemId, "libraryItemId must not be null");
        Map<String, String> values = readRows();
        ReaderDisplayPreferences global = preferences(values, "", ReaderDisplayPreferences.defaults());
        String prefix = titlePrefix(libraryItemId);
        return values.containsKey(prefix + "scaleMode") ? preferences(values, prefix, global) : global;
    }

    @Override
    public synchronized boolean hasOverride(LibraryItemId libraryItemId) {
        Objects.requireNonNull(libraryItemId, "libraryItemId must not be null");
        return readRows().containsKey(titlePrefix(libraryItemId) + "scaleMode");
    }

    @Override
    public synchronized void save(ReaderDisplayPreferences preferences) {
        Map<String, String> values = readRows();
        putPreferences(values, "", Objects.requireNonNull(preferences, "preferences must not be null"));
        write(values);
    }

    @Override
    public synchronized void saveOverride(
            LibraryItemId libraryItemId,
            ReaderDisplayPreferences preferences) {
        Map<String, String> values = readRows();
        putPreferences(
                values,
                titlePrefix(Objects.requireNonNull(libraryItemId, "libraryItemId must not be null")),
                Objects.requireNonNull(preferences, "preferences must not be null"));
        write(values);
    }

    @Override
    public synchronized void clearOverride(LibraryItemId libraryItemId) {
        String prefix = titlePrefix(Objects.requireNonNull(libraryItemId, "libraryItemId must not be null"));
        Map<String, String> values = readRows();
        values.keySet().removeIf(key -> key.startsWith(prefix));
        write(values);
    }

    private static ReaderDisplayPreferences preferences(
            Map<String, String> values,
            String prefix,
            ReaderDisplayPreferences defaults) {
        try {
            return new ReaderDisplayPreferences(
                    ReaderScaleMode.valueOf(values.getOrDefault(prefix + "scaleMode", defaults.scaleMode().name())),
                    booleanValue(values, prefix + "cropBorders", defaults.cropBorders()),
                    booleanValue(values, prefix + "splitPages", defaults.splitPages()),
                    ReaderRotation.valueOf(values.getOrDefault(prefix + "rotation", defaults.rotation().name())),
                    booleanValue(values, prefix + "dualPage", defaults.dualPage()),
                    integerValue(values, prefix + "webtoonSpacingDp", defaults.webtoonSpacingDp()),
                    ReaderColorFilter.valueOf(values.getOrDefault(
                            prefix + "colorFilter",
                            defaults.colorFilter().name())),
                    integerValue(values, prefix + "brightnessPercent", defaults.brightnessPercent()),
                    ReaderPageTransition.valueOf(values.getOrDefault(
                            prefix + "transition",
                            defaults.transition().name())),
                    ReaderOrientationPolicy.valueOf(values.getOrDefault(
                            prefix + "orientationPolicy",
                            defaults.orientationPolicy().name())));
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("Invalid reader display preference value", exception);
        }
    }

    private static void putPreferences(
            Map<String, String> values,
            String prefix,
            ReaderDisplayPreferences preferences) {
        values.put(prefix + "scaleMode", preferences.scaleMode().name());
        values.put(prefix + "cropBorders", Boolean.toString(preferences.cropBorders()));
        values.put(prefix + "splitPages", Boolean.toString(preferences.splitPages()));
        values.put(prefix + "rotation", preferences.rotation().name());
        values.put(prefix + "dualPage", Boolean.toString(preferences.dualPage()));
        values.put(prefix + "webtoonSpacingDp", Integer.toString(preferences.webtoonSpacingDp()));
        values.put(prefix + "colorFilter", preferences.colorFilter().name());
        values.put(prefix + "brightnessPercent", Integer.toString(preferences.brightnessPercent()));
        values.put(prefix + "transition", preferences.transition().name());
        values.put(prefix + "orientationPolicy", preferences.orientationPolicy().name());
    }

    private Map<String, String> readRows() {
        Map<String, String> values = new LinkedHashMap<>();
        if (!Files.exists(file)) {
            return values;
        }
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

    private void write(Map<String, String> values) {
        Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        try {
            Files.createDirectories(file.getParent());
            Files.write(
                    temporary,
                    values.entrySet().stream()
                            .sorted(Map.Entry.comparingByKey())
                            .map(entry -> entry.getKey() + "=" + entry.getValue())
                            .toList(),
                    StandardCharsets.UTF_8);
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

    private static String titlePrefix(LibraryItemId libraryItemId) {
        String encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(
                libraryItemId.value().getBytes(StandardCharsets.UTF_8));
        return "title." + encoded + ".";
    }

    private static void moveAtomically(Path source, Path destination) throws IOException {
        try {
            Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
