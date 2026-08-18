package fr.vriege.anilib.feature.player.runtime;

import fr.vriege.anilib.feature.library.LibraryItemId;
import fr.vriege.anilib.feature.player.PlaybackState;
import fr.vriege.anilib.feature.player.PlayerException;
import fr.vriege.anilib.feature.source.SourceCatalogueItemId;
import fr.vriege.anilib.feature.source.SourceEpisodeId;
import fr.vriege.anilib.feature.source.SourceId;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

final class PlaybackStateStore {
    private static final String HEADER = "ANILIB_PLAYBACK\t1";
    private static final long MAXIMUM_FILE_BYTES = 64L * 1024L * 1024L;
    private static final int MAXIMUM_ENTRIES = 1_000_000;
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();
    private static final Comparator<PlaybackState> ORDER = Comparator
            .comparing((PlaybackState state) -> state.libraryItemId().value())
            .thenComparing(state -> state.episodeId().itemId().sourceId())
            .thenComparing(state -> state.episodeId().itemId().value())
            .thenComparing(state -> state.episodeId().value());
    private final Path file;
    private final Map<Key, PlaybackState> states = new LinkedHashMap<>();

    PlaybackStateStore(Path file) {
        this.file = Objects.requireNonNull(file, "file must not be null").toAbsolutePath().normalize();
        load();
    }

    synchronized Optional<PlaybackState> find(LibraryItemId itemId, SourceEpisodeId episodeId) {
        return Optional.ofNullable(states.get(new Key(itemId, episodeId)));
    }

    synchronized List<PlaybackState> snapshot() {
        return states.values().stream().sorted(ORDER).toList();
    }

    synchronized void save(PlaybackState state) {
        Map<Key, PlaybackState> replacement = new LinkedHashMap<>(states);
        replacement.put(Key.of(state), state);
        persistAndReplace(replacement);
    }

    synchronized void restore(
            LibraryItemId itemId,
            SourceEpisodeId episodeId,
            Optional<PlaybackState> previous) {
        Map<Key, PlaybackState> replacement = new LinkedHashMap<>(states);
        Key key = new Key(itemId, episodeId);
        if (previous.isPresent()) {
            replacement.put(key, previous.orElseThrow());
        } else {
            replacement.remove(key);
        }
        persistAndReplace(replacement);
    }

    synchronized void replaceAll(Collection<PlaybackState> replacement) {
        Objects.requireNonNull(replacement, "replacement must not be null");
        Map<Key, PlaybackState> indexed = new LinkedHashMap<>();
        for (PlaybackState state : replacement) {
            PlaybackState value = Objects.requireNonNull(state, "replacement must not contain null");
            if (indexed.putIfAbsent(Key.of(value), value) != null) {
                throw new IllegalArgumentException("replacement must not contain duplicate playback states");
            }
        }
        persistAndReplace(indexed);
    }

    private void load() {
        if (!Files.exists(file)) {
            return;
        }
        if (!Files.isRegularFile(file) || Files.isSymbolicLink(file)) {
            throw new PlayerException("Playback state file must be a regular non-symbolic file");
        }
        try {
            if (Files.size(file) > MAXIMUM_FILE_BYTES) {
                throw new PlayerException("Playback state file exceeds the supported size");
            }
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            if (lines.isEmpty() || !lines.getFirst().equals(HEADER)) {
                throw new PlayerException("Unsupported playback state format");
            }
            if (lines.size() - 1 > MAXIMUM_ENTRIES) {
                throw new PlayerException("Playback state contains too many entries");
            }
            for (int index = 1; index < lines.size(); index++) {
                if (!lines.get(index).isBlank()) {
                    PlaybackState state = decode(lines.get(index));
                    if (states.putIfAbsent(Key.of(state), state) != null) {
                        throw new PlayerException("Playback state contains duplicate entries");
                    }
                }
            }
        } catch (IOException exception) {
            throw new PlayerException("Unable to load playback state", exception);
        }
    }

    private void persistAndReplace(Map<Key, PlaybackState> replacement) {
        if (replacement.size() > MAXIMUM_ENTRIES) {
            throw new PlayerException("Playback state contains too many entries");
        }
        Path parent = file.getParent();
        Path temporary = null;
        try {
            if (parent != null) {
                Files.createDirectories(parent);
                if (!Files.isDirectory(parent) || Files.isSymbolicLink(parent)) {
                    throw new PlayerException("Playback state directory must be non-symbolic");
                }
            }
            List<String> lines = new ArrayList<>();
            lines.add(HEADER);
            replacement.values().stream().sorted(ORDER).map(PlaybackStateStore::encode).forEach(lines::add);
            temporary = Files.createTempFile(parent, ".anilib-playback-", ".tmp");
            Files.write(temporary, lines, StandardCharsets.UTF_8);
            moveAtomically(temporary, file);
            temporary = null;
            states.clear();
            states.putAll(replacement);
        } catch (IOException exception) {
            throw new PlayerException("Unable to store playback state", exception);
        } finally {
            deleteTemporary(temporary);
        }
    }

    private static String encode(PlaybackState state) {
        return String.join("\t",
                "STATE",
                text(state.libraryItemId().value()),
                state.episodeId().itemId().sourceId().toString(),
                text(state.episodeId().itemId().value()),
                text(state.episodeId().value()),
                Long.toString(state.positionMillis()),
                Long.toString(state.durationMillis()),
                Boolean.toString(state.completed()),
                state.updatedAt().toString());
    }

    private static PlaybackState decode(String line) {
        try {
            String[] fields = line.split("\t", -1);
            if (fields.length != 9 || !fields[0].equals("STATE")) {
                throw new IllegalArgumentException("invalid field count");
            }
            SourceCatalogueItemId itemId = new SourceCatalogueItemId(
                    SourceId.of(fields[2]),
                    plain(fields[3]));
            return new PlaybackState(
                    new LibraryItemId(plain(fields[1])),
                    new SourceEpisodeId(itemId, plain(fields[4])),
                    Long.parseLong(fields[5]),
                    Long.parseLong(fields[6]),
                    parseBoolean(fields[7]),
                    Instant.parse(fields[8]));
        } catch (IllegalArgumentException exception) {
            throw new PlayerException("Invalid playback state entry", exception);
        }
    }

    private static void moveAtomically(Path source, Path destination) throws IOException {
        try {
            Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            throw new IOException("Atomic playback state replacement is unavailable", exception);
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

    private record Key(LibraryItemId libraryItemId, SourceEpisodeId episodeId) {
        private Key {
            Objects.requireNonNull(libraryItemId, "libraryItemId must not be null");
            Objects.requireNonNull(episodeId, "episodeId must not be null");
        }

        private static Key of(PlaybackState state) {
            return new Key(state.libraryItemId(), state.episodeId());
        }
    }
}
