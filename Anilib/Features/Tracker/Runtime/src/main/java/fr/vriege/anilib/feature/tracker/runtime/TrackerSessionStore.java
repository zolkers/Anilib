package fr.vriege.anilib.feature.tracker.runtime;

import fr.vriege.anilib.feature.tracker.TrackerAuthentication;
import fr.vriege.anilib.feature.tracker.TrackerCredentials;
import fr.vriege.anilib.feature.tracker.TrackerException;
import fr.vriege.anilib.feature.tracker.TrackerId;
import fr.vriege.anilib.feature.tracker.TrackerSession;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

final class TrackerSessionStore {
    private static final String HEADER = "ANILIB_TRACKER_SESSIONS\t1";
    private static final long MAXIMUM_FILE_BYTES = 64L * 1024L;
    private static final int MAXIMUM_SESSIONS = 32;
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();
    private final Path file;
    private final Map<TrackerId, TrackerSession> sessions = new LinkedHashMap<>();

    TrackerSessionStore(Path file) {
        this.file = Objects.requireNonNull(file, "file must not be null").toAbsolutePath().normalize();
        load();
    }

    synchronized Optional<TrackerSession> find(TrackerId trackerId) {
        return Optional.ofNullable(sessions.get(Objects.requireNonNull(
                trackerId,
                "trackerId must not be null")));
    }

    synchronized void save(TrackerId trackerId, TrackerSession session) {
        TrackerId id = Objects.requireNonNull(trackerId, "trackerId must not be null");
        TrackerSession value = Objects.requireNonNull(session, "session must not be null");
        Map<TrackerId, TrackerSession> replacement = new LinkedHashMap<>(sessions);
        replacement.put(id, value);
        persistAndReplace(replacement);
    }

    synchronized void remove(TrackerId trackerId) {
        TrackerId id = Objects.requireNonNull(trackerId, "trackerId must not be null");
        if (!sessions.containsKey(id)) {
            return;
        }
        Map<TrackerId, TrackerSession> replacement = new LinkedHashMap<>(sessions);
        replacement.remove(id);
        persistAndReplace(replacement);
    }

    private void load() {
        if (!Files.exists(file)) {
            return;
        }
        if (!Files.isRegularFile(file) || Files.isSymbolicLink(file)) {
            throw new TrackerException("Tracker session file must be a regular non-symbolic file");
        }
        try {
            if (Files.size(file) > MAXIMUM_FILE_BYTES) {
                throw new TrackerException("Tracker session file exceeds the supported size");
            }
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            if (lines.isEmpty() || !lines.getFirst().equals(HEADER)) {
                throw new TrackerException("Unsupported tracker session format");
            }
            if (lines.size() - 1 > MAXIMUM_SESSIONS) {
                throw new TrackerException("Tracker session file contains too many accounts");
            }
            for (int index = 1; index < lines.size(); index++) {
                if (!lines.get(index).isBlank()) {
                    Map.Entry<TrackerId, TrackerSession> decoded = decode(lines.get(index));
                    if (sessions.putIfAbsent(decoded.getKey(), decoded.getValue()) != null) {
                        throw new TrackerException("Tracker session file contains duplicate accounts");
                    }
                }
            }
        } catch (IOException exception) {
            throw new TrackerException("Unable to load tracker sessions", exception);
        }
    }

    private void persistAndReplace(Map<TrackerId, TrackerSession> replacement) {
        if (replacement.size() > MAXIMUM_SESSIONS) {
            throw new TrackerException("Tracker session file contains too many accounts");
        }
        Path parent = file.getParent();
        Path temporary = null;
        try {
            if (parent != null) {
                Files.createDirectories(parent);
                if (!Files.isDirectory(parent) || Files.isSymbolicLink(parent)) {
                    throw new TrackerException("Tracker session directory must be non-symbolic");
                }
            }
            List<String> lines = new ArrayList<>();
            lines.add(HEADER);
            replacement.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey(Comparator.naturalOrder()))
                    .map(TrackerSessionStore::encode)
                    .forEach(lines::add);
            temporary = Files.createTempFile(parent, ".anilib-tracker-sessions-", ".tmp");
            restrictToOwner(temporary);
            Files.write(temporary, lines, StandardCharsets.UTF_8);
            moveAtomically(temporary, file);
            temporary = null;
            sessions.clear();
            sessions.putAll(replacement);
        } catch (IOException exception) {
            throw new TrackerException("Unable to store tracker sessions", exception);
        } finally {
            deleteTemporary(temporary);
        }
    }

    private static String encode(Map.Entry<TrackerId, TrackerSession> entry) {
        TrackerCredentials credentials = entry.getValue().credentials();
        return String.join("\t",
                "SESSION",
                text(entry.getKey().value()),
                credentials.authentication().name(),
                text(credentials.identity()),
                text(credentials.secret()),
                text(entry.getValue().accountName()));
    }

    private static Map.Entry<TrackerId, TrackerSession> decode(String line) {
        try {
            String[] fields = line.split("\t", -1);
            if (fields.length != 6 || !fields[0].equals("SESSION")) {
                throw new IllegalArgumentException("invalid field count");
            }
            TrackerAuthentication authentication = TrackerAuthentication.valueOf(fields[2]);
            TrackerCredentials credentials = new TrackerCredentials(
                    authentication,
                    plain(fields[3]),
                    plain(fields[4]));
            return Map.entry(
                    TrackerId.of(plain(fields[1])),
                    new TrackerSession(credentials, plain(fields[5])));
        } catch (IllegalArgumentException exception) {
            throw new TrackerException("Invalid tracker session entry", exception);
        }
    }

    private static void restrictToOwner(Path target) throws IOException {
        if (Files.getFileAttributeView(
                target,
                PosixFileAttributeView.class,
                LinkOption.NOFOLLOW_LINKS) != null) {
            Files.setPosixFilePermissions(target, EnumSet.of(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE));
        }
    }

    private static void moveAtomically(Path source, Path destination) throws IOException {
        try {
            Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            throw new IOException("Atomic tracker session replacement is unavailable", exception);
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
}
