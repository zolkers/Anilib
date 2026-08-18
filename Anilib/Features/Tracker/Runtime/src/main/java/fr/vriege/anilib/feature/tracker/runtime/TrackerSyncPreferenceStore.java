package fr.vriege.anilib.feature.tracker.runtime;

import fr.vriege.anilib.feature.tracker.TrackerConflictPolicy;
import fr.vriege.anilib.feature.tracker.TrackerSyncDirection;
import fr.vriege.anilib.feature.tracker.TrackerSyncPreferences;

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

final class TrackerSyncPreferenceStore {
    private final Path file;

    TrackerSyncPreferenceStore(Path file) {
        this.file = Objects.requireNonNull(file, "file must not be null").toAbsolutePath().normalize();
    }

    synchronized TrackerSyncPreferences load() {
        if (!Files.exists(file)) {
            return TrackerSyncPreferences.defaults();
        }
        Map<String, String> values = new LinkedHashMap<>();
        try {
            for (String row : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                String[] columns = row.split("=", -1);
                if (columns.length != 2 || values.putIfAbsent(columns[0], columns[1]) != null) {
                    throw new IllegalStateException("Invalid tracker synchronization preference row");
                }
            }
            TrackerSyncPreferences defaults = TrackerSyncPreferences.defaults();
            return new TrackerSyncPreferences(
                    Boolean.parseBoolean(values.getOrDefault(
                            "automatic", Boolean.toString(defaults.automatic()))),
                    TrackerSyncDirection.valueOf(values.getOrDefault(
                            "direction", defaults.direction().name())),
                    TrackerConflictPolicy.valueOf(values.getOrDefault(
                            "conflictPolicy", defaults.conflictPolicy().name())));
        } catch (IOException exception) {
            throw new UncheckedIOException("Unable to read tracker synchronization preferences", exception);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("Invalid tracker synchronization preference value", exception);
        }
    }

    synchronized void save(TrackerSyncPreferences preferences) {
        TrackerSyncPreferences value = Objects.requireNonNull(preferences, "preferences must not be null");
        Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        List<String> rows = List.of(
                "automatic=" + value.automatic(),
                "conflictPolicy=" + value.conflictPolicy().name(),
                "direction=" + value.direction().name());
        try {
            Files.createDirectories(file.getParent());
            Files.write(temporary, rows, StandardCharsets.UTF_8);
            moveAtomically(temporary, file);
        } catch (IOException exception) {
            throw new UncheckedIOException("Unable to write tracker synchronization preferences", exception);
        } finally {
            try {
                Files.deleteIfExists(temporary);
            } catch (IOException ignored) {
                // The primary operation reports the actionable error.
            }
        }
    }

    private static void moveAtomically(Path source, Path destination) throws IOException {
        try {
            Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
