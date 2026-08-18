package fr.vriege.anilib.feature.player.runtime;

import fr.vriege.anilib.feature.library.LibraryItemId;
import fr.vriege.anilib.feature.player.PlayerDecoderPolicy;
import fr.vriege.anilib.feature.player.PlayerPreferenceStore;
import fr.vriege.anilib.feature.player.PlayerPreferences;
import fr.vriege.anilib.feature.player.PlayerQualityPolicy;
import fr.vriege.anilib.feature.player.PlayerSubtitlePolicy;

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
import java.util.Optional;

public final class FilePlayerPreferenceStore implements PlayerPreferenceStore {
    private final Path file;

    public FilePlayerPreferenceStore(Path file) {
        this.file = Objects.requireNonNull(file, "file must not be null").toAbsolutePath().normalize();
    }

    @Override
    public synchronized PlayerPreferences snapshot() {
        return preferences(readRows(), "", PlayerPreferences.defaults());
    }

    @Override
    public synchronized PlayerPreferences snapshot(LibraryItemId libraryItemId) {
        Objects.requireNonNull(libraryItemId, "libraryItemId must not be null");
        Map<String, String> values = readRows();
        PlayerPreferences global = preferences(values, "", PlayerPreferences.defaults());
        String prefix = titlePrefix(libraryItemId);
        return values.containsKey(prefix + "decoderPolicy") ? preferences(values, prefix, global) : global;
    }

    @Override
    public synchronized boolean hasOverride(LibraryItemId libraryItemId) {
        Objects.requireNonNull(libraryItemId, "libraryItemId must not be null");
        return readRows().containsKey(titlePrefix(libraryItemId) + "decoderPolicy");
    }

    @Override
    public synchronized void save(PlayerPreferences preferences) {
        Map<String, String> values = readRows();
        putPreferences(values, "", Objects.requireNonNull(preferences, "preferences must not be null"));
        write(values);
    }

    @Override
    public synchronized void saveOverride(
            LibraryItemId libraryItemId,
            PlayerPreferences preferences) {
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

    private static PlayerPreferences preferences(
            Map<String, String> values,
            String prefix,
            PlayerPreferences defaults) {
        try {
            return new PlayerPreferences(
                    PlayerDecoderPolicy.valueOf(values.getOrDefault(
                            prefix + "decoderPolicy",
                            defaults.decoderPolicy().name())),
                    optional(values, prefix + "audioLanguage", defaults.preferredAudioLanguage()),
                    PlayerSubtitlePolicy.valueOf(values.getOrDefault(
                            prefix + "subtitlePolicy",
                            defaults.subtitlePolicy().name())),
                    optional(values, prefix + "subtitleLanguage", defaults.preferredSubtitleLanguage()),
                    PlayerQualityPolicy.valueOf(values.getOrDefault(
                            prefix + "qualityPolicy",
                            defaults.qualityPolicy().name())),
                    optional(values, prefix + "preferredQuality", defaults.preferredQuality()),
                    longValue(values, prefix + "introEndMillis", defaults.introEndMillis()),
                    longValue(values, prefix + "outroDurationMillis", defaults.outroDurationMillis()));
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("Invalid player preference value", exception);
        }
    }

    private static void putPreferences(
            Map<String, String> values,
            String prefix,
            PlayerPreferences preferences) {
        values.put(prefix + "decoderPolicy", preferences.decoderPolicy().name());
        putOptional(values, prefix + "audioLanguage", preferences.preferredAudioLanguage());
        values.put(prefix + "subtitlePolicy", preferences.subtitlePolicy().name());
        putOptional(values, prefix + "subtitleLanguage", preferences.preferredSubtitleLanguage());
        values.put(prefix + "qualityPolicy", preferences.qualityPolicy().name());
        putOptional(values, prefix + "preferredQuality", preferences.preferredQuality());
        values.put(prefix + "introEndMillis", Long.toString(preferences.introEndMillis()));
        values.put(prefix + "outroDurationMillis", Long.toString(preferences.outroDurationMillis()));
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
                    throw new IllegalStateException("Invalid player preference row");
                }
            }
            return values;
        } catch (IOException exception) {
            throw new UncheckedIOException("Unable to read player preferences", exception);
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
            throw new UncheckedIOException("Unable to write player preferences", exception);
        } finally {
            try {
                Files.deleteIfExists(temporary);
            } catch (IOException ignored) {
                // The primary operation reports the actionable error.
            }
        }
    }

    private static Optional<String> optional(
            Map<String, String> values,
            String key,
            Optional<String> fallback) {
        String value = values.get(key);
        return value == null ? fallback : Optional.of(value).filter(candidate -> !candidate.isEmpty());
    }

    private static void putOptional(Map<String, String> values, String key, Optional<String> value) {
        values.put(key, value.orElse(""));
    }

    private static long longValue(Map<String, String> values, String key, long fallback) {
        String value = values.get(key);
        return value == null ? fallback : Long.parseLong(value);
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
