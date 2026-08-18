package fr.vriege.anilib.feature.tracker.runtime;

import fr.vriege.anilib.feature.library.LibraryItemId;
import fr.vriege.anilib.feature.tracker.TrackerId;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Base64;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

final class TrackerPendingSyncStore {
    private final Path file;

    TrackerPendingSyncStore(Path file) {
        this.file = Objects.requireNonNull(file, "file must not be null").toAbsolutePath().normalize();
    }

    synchronized Set<PendingSync> load() {
        if (!Files.exists(file)) {
            return Set.of();
        }
        Set<PendingSync> values = new LinkedHashSet<>();
        try {
            for (String row : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                String[] columns = row.split("\\t", -1);
                if (columns.length != 2 || !values.add(new PendingSync(
                        new LibraryItemId(decode(columns[0])),
                        TrackerId.of(decode(columns[1]))))) {
                    throw new IllegalStateException("Invalid pending tracker synchronization row");
                }
            }
            return Set.copyOf(values);
        } catch (IOException exception) {
            throw new UncheckedIOException("Unable to read pending tracker synchronizations", exception);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("Invalid pending tracker synchronization identity", exception);
        }
    }

    synchronized void save(Collection<PendingSync> pending) {
        List<String> rows = List.copyOf(pending).stream()
                .map(value -> encode(value.libraryItemId().value()) + "\t" + encode(value.trackerId().value()))
                .sorted()
                .toList();
        Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        try {
            Files.createDirectories(file.getParent());
            Files.write(temporary, rows, StandardCharsets.UTF_8);
            moveAtomically(temporary, file);
        } catch (IOException exception) {
            throw new UncheckedIOException("Unable to write pending tracker synchronizations", exception);
        } finally {
            try {
                Files.deleteIfExists(temporary);
            } catch (IOException ignored) {
                // The primary operation reports the actionable error.
            }
        }
    }

    private static String encode(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String decode(String value) {
        return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
    }

    private static void moveAtomically(Path source, Path destination) throws IOException {
        try {
            Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    record PendingSync(LibraryItemId libraryItemId, TrackerId trackerId) {
        PendingSync {
            Objects.requireNonNull(libraryItemId, "libraryItemId must not be null");
            Objects.requireNonNull(trackerId, "trackerId must not be null");
        }
    }
}
