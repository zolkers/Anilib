package fr.vriege.anilib.feature.tracker.runtime;

import fr.vriege.anilib.feature.library.LibraryItemId;
import fr.vriege.anilib.feature.tracker.TrackerEntry;
import fr.vriege.anilib.feature.tracker.TrackerException;
import fr.vriege.anilib.feature.tracker.TrackerId;
import fr.vriege.anilib.feature.tracker.TrackerStatus;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;

final class TrackerEntryStore {
    private static final String HEADER = "ANILIB_TRACKING\t1";
    private static final long MAXIMUM_FILE_BYTES = 64L * 1024L * 1024L;
    private static final int MAXIMUM_ENTRIES = 1_000_000;
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();
    private static final Comparator<TrackerEntry> ORDER = Comparator
            .comparing((TrackerEntry entry) -> entry.libraryItemId().value())
            .thenComparing(TrackerEntry::trackerId);
    private final Path file;
    private final Map<Key, TrackerEntry> entries = new LinkedHashMap<>();

    TrackerEntryStore(Path file) {
        this.file = Objects.requireNonNull(file, "file must not be null").toAbsolutePath().normalize();
        load();
    }

    synchronized Optional<TrackerEntry> find(LibraryItemId itemId, TrackerId trackerId) {
        return Optional.ofNullable(entries.get(new Key(itemId, trackerId)));
    }

    synchronized List<TrackerEntry> forItem(LibraryItemId itemId) {
        return entries.values().stream()
                .filter(entry -> entry.libraryItemId().equals(itemId))
                .sorted(ORDER)
                .toList();
    }

    synchronized List<TrackerEntry> snapshot() {
        return entries.values().stream().sorted(ORDER).toList();
    }

    synchronized void save(TrackerEntry entry) {
        Map<Key, TrackerEntry> replacement = new LinkedHashMap<>(entries);
        replacement.put(Key.of(entry), entry);
        persistAndReplace(replacement);
    }

    synchronized boolean remove(LibraryItemId itemId, TrackerId trackerId) {
        Key key = new Key(itemId, trackerId);
        if (!entries.containsKey(key)) {
            return false;
        }
        Map<Key, TrackerEntry> replacement = new LinkedHashMap<>(entries);
        replacement.remove(key);
        persistAndReplace(replacement);
        return true;
    }

    synchronized void replaceAll(Collection<TrackerEntry> replacement) {
        Objects.requireNonNull(replacement, "replacement must not be null");
        Map<Key, TrackerEntry> indexed = new LinkedHashMap<>();
        for (TrackerEntry entry : replacement) {
            TrackerEntry value = Objects.requireNonNull(entry, "replacement must not contain null");
            if (indexed.putIfAbsent(Key.of(value), value) != null) {
                throw new IllegalArgumentException("replacement contains duplicate tracker entries");
            }
        }
        persistAndReplace(indexed);
    }

    private void load() {
        if (!Files.exists(file)) {
            return;
        }
        if (!Files.isRegularFile(file) || Files.isSymbolicLink(file)) {
            throw new TrackerException("Tracker state file must be a regular non-symbolic file");
        }
        try {
            if (Files.size(file) > MAXIMUM_FILE_BYTES) {
                throw new TrackerException("Tracker state file exceeds the supported size");
            }
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            if (lines.isEmpty() || !lines.getFirst().equals(HEADER)) {
                throw new TrackerException("Unsupported tracker state format");
            }
            if (lines.size() - 1 > MAXIMUM_ENTRIES) {
                throw new TrackerException("Tracker state contains too many entries");
            }
            for (int index = 1; index < lines.size(); index++) {
                if (!lines.get(index).isBlank()) {
                    TrackerEntry entry = decode(lines.get(index));
                    if (entries.putIfAbsent(Key.of(entry), entry) != null) {
                        throw new TrackerException("Tracker state contains duplicate entries");
                    }
                }
            }
        } catch (IOException exception) {
            throw new TrackerException("Unable to load tracker state", exception);
        }
    }

    private void persistAndReplace(Map<Key, TrackerEntry> replacement) {
        if (replacement.size() > MAXIMUM_ENTRIES) {
            throw new TrackerException("Tracker state contains too many entries");
        }
        Path parent = file.getParent();
        Path temporary = null;
        try {
            if (parent != null) {
                Files.createDirectories(parent);
                if (!Files.isDirectory(parent) || Files.isSymbolicLink(parent)) {
                    throw new TrackerException("Tracker state directory must be non-symbolic");
                }
            }
            List<String> lines = new ArrayList<>();
            lines.add(HEADER);
            replacement.values().stream().sorted(ORDER).map(TrackerEntryStore::encode).forEach(lines::add);
            temporary = Files.createTempFile(parent, ".anilib-tracking-", ".tmp");
            Files.write(temporary, lines, StandardCharsets.UTF_8);
            moveAtomically(temporary, file);
            temporary = null;
            entries.clear();
            entries.putAll(replacement);
        } catch (IOException exception) {
            throw new TrackerException("Unable to store tracker state", exception);
        } finally {
            deleteTemporary(temporary);
        }
    }

    private static String encode(TrackerEntry entry) {
        return String.join("\t",
                "ENTRY",
                text(entry.libraryItemId().value()),
                text(entry.trackerId().value()),
                text(entry.remoteId()),
                text(entry.title()),
                Double.toString(entry.progress()),
                Long.toString(entry.totalUnits()),
                entry.status().name(),
                entry.score().isPresent() ? Double.toString(entry.score().getAsDouble()) : "",
                entry.startDate().map(LocalDate::toString).orElse(""),
                entry.finishDate().map(LocalDate::toString).orElse(""),
                Boolean.toString(entry.privateEntry()),
                entry.remoteUri().map(URI::toString).map(TrackerEntryStore::text).orElse(""),
                entry.updatedAt().toString());
    }

    private static TrackerEntry decode(String line) {
        try {
            String[] fields = line.split("\t", -1);
            if (fields.length != 14 || !fields[0].equals("ENTRY")) {
                throw new IllegalArgumentException("invalid field count");
            }
            return new TrackerEntry(
                    new LibraryItemId(plain(fields[1])),
                    TrackerId.of(plain(fields[2])),
                    plain(fields[3]),
                    plain(fields[4]),
                    Double.parseDouble(fields[5]),
                    Long.parseLong(fields[6]),
                    TrackerStatus.valueOf(fields[7]),
                    fields[8].isEmpty() ? OptionalDouble.empty() : OptionalDouble.of(Double.parseDouble(fields[8])),
                    fields[9].isEmpty() ? Optional.empty() : Optional.of(LocalDate.parse(fields[9])),
                    fields[10].isEmpty() ? Optional.empty() : Optional.of(LocalDate.parse(fields[10])),
                    parseBoolean(fields[11]),
                    fields[12].isEmpty() ? Optional.empty() : Optional.of(URI.create(plain(fields[12]))),
                    Instant.parse(fields[13]));
        } catch (IllegalArgumentException exception) {
            throw new TrackerException("Invalid tracker state entry", exception);
        }
    }

    private static void moveAtomically(Path source, Path destination) throws IOException {
        try {
            Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            throw new IOException("Atomic tracker state replacement is unavailable", exception);
        }
    }

    private static void deleteTemporary(Path temporary) {
        if (temporary == null) {
            return;
        }
        try {
            Files.deleteIfExists(temporary);
        } catch (IOException ignored) {
            // The primary operation reports the actionable error.
        }
    }

    private static String text(String value) {
        return ENCODER.encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String plain(String value) {
        return new String(DECODER.decode(value), StandardCharsets.UTF_8);
    }

    private static boolean parseBoolean(String value) {
        if (value.equals("true")) {
            return true;
        }
        if (value.equals("false")) {
            return false;
        }
        throw new IllegalArgumentException("invalid boolean value");
    }

    private record Key(LibraryItemId itemId, TrackerId trackerId) {
        private static Key of(TrackerEntry entry) {
            return new Key(entry.libraryItemId(), entry.trackerId());
        }
    }
}
