package fr.vriege.anilib.feature.updates.runtime;

import fr.vriege.anilib.feature.library.LibraryItemId;
import fr.vriege.anilib.feature.library.MediaKind;
import fr.vriege.anilib.feature.updates.LibraryUpdateEvent;
import fr.vriege.anilib.feature.updates.LibraryUpdateException;
import fr.vriege.anilib.feature.updates.LibraryUpdatePolicy;
import fr.vriege.anilib.feature.updates.UpdateInterval;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

final class LibraryUpdateStore {
    private static final int MAGIC = 0x55504454;
    private static final int VERSION = 1;
    private static final int MAXIMUM_BASELINES = 1_000_000;
    private static final int MAXIMUM_CONTENT_IDS = 10_000_000;
    private static final int MAXIMUM_EVENTS = 100_000;
    private static final int MAXIMUM_CATEGORIES = 10_000;
    private static final int MAXIMUM_STRING_BYTES = 1024 * 1024;
    private static final long MAXIMUM_FILE_BYTES = 256L * 1024L * 1024L;
    private static final Comparator<LibraryUpdateEvent> EVENT_ORDER = Comparator
            .comparing(LibraryUpdateEvent::discoveredAt)
            .reversed()
            .thenComparing(event -> event.libraryItemId().value())
            .thenComparing(LibraryUpdateEvent::sourceContentId);
    private final Path file;
    private State state;

    LibraryUpdateStore(Path file) {
        this.file = Objects.requireNonNull(file, "file must not be null").toAbsolutePath().normalize();
        state = load();
    }

    synchronized State snapshot() {
        return state;
    }

    synchronized void replace(State replacement) {
        State value = Objects.requireNonNull(replacement, "replacement must not be null");
        byte[] encoded = encode(value);
        Path parent = file.getParent();
        Path temporary = null;
        try {
            if (parent != null) {
                Files.createDirectories(parent);
                if (!Files.isDirectory(parent) || Files.isSymbolicLink(parent)) {
                    throw new LibraryUpdateException("Update state directory must be non-symbolic");
                }
            }
            temporary = Files.createTempFile(parent, ".anilib-updates-", ".tmp");
            Files.write(temporary, encoded);
            moveAtomically(temporary, file);
            temporary = null;
            state = value;
        } catch (IOException exception) {
            throw new LibraryUpdateException("Unable to store library update state", exception);
        } finally {
            deleteTemporary(temporary);
        }
    }

    synchronized byte[] exportState() {
        return encode(state);
    }

    static State decodeState(byte[] payload) {
        return decode(Objects.requireNonNull(payload, "payload must not be null"));
    }

    private State load() {
        if (!Files.exists(file)) {
            return State.empty();
        }
        if (!Files.isRegularFile(file) || Files.isSymbolicLink(file)) {
            throw new LibraryUpdateException("Update state file must be a regular non-symbolic file");
        }
        try {
            if (Files.size(file) > MAXIMUM_FILE_BYTES) {
                throw new LibraryUpdateException("Update state file exceeds the supported size");
            }
            return decode(Files.readAllBytes(file));
        } catch (IOException exception) {
            throw new LibraryUpdateException("Unable to load library update state", exception);
        }
    }

    private static byte[] encode(State state) {
        validateSize(state);
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream output = new DataOutputStream(bytes)) {
                output.writeInt(MAGIC);
                output.writeInt(VERSION);
                writePolicy(output, state.policy());
                output.writeBoolean(state.lastRunAt().isPresent());
                if (state.lastRunAt().isPresent()) {
                    writeString(output, state.lastRunAt().orElseThrow().toString());
                }
                output.writeInt(state.baselines().size());
                List<Map.Entry<LibraryItemId, Set<String>>> baselines = state.baselines().entrySet().stream()
                        .sorted(Map.Entry.comparingByKey(Comparator.comparing(LibraryItemId::value)))
                        .toList();
                for (Map.Entry<LibraryItemId, Set<String>> baseline : baselines) {
                    writeString(output, baseline.getKey().value());
                    List<String> contentIds = baseline.getValue().stream().sorted().toList();
                    output.writeInt(contentIds.size());
                    for (String contentId : contentIds) {
                        writeString(output, contentId);
                    }
                }
                List<LibraryUpdateEvent> events = state.events().stream().sorted(EVENT_ORDER).toList();
                output.writeInt(events.size());
                for (LibraryUpdateEvent event : events) {
                    writeEvent(output, event);
                }
            }
            byte[] encoded = bytes.toByteArray();
            if (encoded.length > MAXIMUM_FILE_BYTES) {
                throw new LibraryUpdateException("Update state exceeds the supported file size");
            }
            return encoded;
        } catch (IOException exception) {
            throw new LibraryUpdateException("Unable to encode library update state", exception);
        }
    }

    private static State decode(byte[] payload) {
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(payload))) {
            if (input.readInt() != MAGIC || input.readInt() != VERSION) {
                throw new LibraryUpdateException("Update state signature or version is invalid");
            }
            LibraryUpdatePolicy policy = readPolicy(input);
            Optional<Instant> lastRunAt = input.readBoolean()
                    ? Optional.of(Instant.parse(readString(input)))
                    : Optional.empty();
            int baselineCount = boundedCount(input.readInt(), MAXIMUM_BASELINES, "baseline");
            Map<LibraryItemId, Set<String>> baselines = new LinkedHashMap<>();
            long contentCount = 0;
            for (int index = 0; index < baselineCount; index++) {
                LibraryItemId itemId = new LibraryItemId(readString(input));
                int count = boundedCount(input.readInt(), MAXIMUM_CONTENT_IDS, "content id");
                contentCount += count;
                if (contentCount > MAXIMUM_CONTENT_IDS) {
                    throw new LibraryUpdateException("Update state contains too many content identities");
                }
                Set<String> contentIds = new LinkedHashSet<>();
                for (int contentIndex = 0; contentIndex < count; contentIndex++) {
                    if (!contentIds.add(readString(input))) {
                        throw new LibraryUpdateException("Update baseline contains duplicate content identities");
                    }
                }
                if (baselines.putIfAbsent(itemId, Set.copyOf(contentIds)) != null) {
                    throw new LibraryUpdateException("Update state contains duplicate baselines");
                }
            }
            int eventCount = boundedCount(input.readInt(), MAXIMUM_EVENTS, "event");
            List<LibraryUpdateEvent> events = new ArrayList<>();
            Set<EventKey> eventKeys = new LinkedHashSet<>();
            for (int index = 0; index < eventCount; index++) {
                LibraryUpdateEvent event = readEvent(input);
                if (!eventKeys.add(EventKey.of(event))) {
                    throw new LibraryUpdateException("Update state contains duplicate events");
                }
                events.add(event);
            }
            if (input.read() != -1) {
                throw new LibraryUpdateException("Unexpected trailing update state data");
            }
            return new State(policy, baselines, events, lastRunAt);
        } catch (EOFException exception) {
            throw new LibraryUpdateException("Truncated library update state", exception);
        } catch (IOException | IllegalArgumentException exception) {
            throw new LibraryUpdateException("Invalid library update state", exception);
        }
    }

    private static void writePolicy(DataOutputStream output, LibraryUpdatePolicy policy) throws IOException {
        writeString(output, policy.interval().name());
        output.writeBoolean(policy.favoritesOnly());
        output.writeBoolean(policy.skipCompleted());
        output.writeBoolean(policy.skipNotStarted());
        writeStrings(output, policy.includedCategories());
        writeStrings(output, policy.excludedCategories());
    }

    private static LibraryUpdatePolicy readPolicy(DataInputStream input) throws IOException {
        UpdateInterval interval = UpdateInterval.valueOf(readString(input));
        boolean favoritesOnly = input.readBoolean();
        boolean skipCompleted = input.readBoolean();
        boolean skipNotStarted = input.readBoolean();
        Set<String> included = readStrings(input, MAXIMUM_CATEGORIES);
        Set<String> excluded = readStrings(input, MAXIMUM_CATEGORIES);
        return new LibraryUpdatePolicy(
                interval,
                favoritesOnly,
                skipCompleted,
                skipNotStarted,
                included,
                excluded);
    }

    private static void writeEvent(DataOutputStream output, LibraryUpdateEvent event) throws IOException {
        writeString(output, event.libraryItemId().value());
        writeString(output, event.libraryTitle());
        writeString(output, event.kind().name());
        writeString(output, event.sourceContentId());
        writeString(output, event.contentTitle());
        output.writeBoolean(event.publishedAt().isPresent());
        if (event.publishedAt().isPresent()) {
            writeString(output, event.publishedAt().orElseThrow().toString());
        }
        writeString(output, event.discoveredAt().toString());
        output.writeBoolean(event.read());
    }

    private static LibraryUpdateEvent readEvent(DataInputStream input) throws IOException {
        return new LibraryUpdateEvent(
                new LibraryItemId(readString(input)),
                readString(input),
                MediaKind.valueOf(readString(input)),
                readString(input),
                readString(input),
                input.readBoolean() ? Optional.of(Instant.parse(readString(input))) : Optional.empty(),
                Instant.parse(readString(input)),
                input.readBoolean());
    }

    private static void writeStrings(DataOutputStream output, Collection<String> values) throws IOException {
        List<String> ordered = values.stream().sorted().toList();
        output.writeInt(ordered.size());
        for (String value : ordered) {
            writeString(output, value);
        }
    }

    private static Set<String> readStrings(DataInputStream input, int maximum) throws IOException {
        int count = boundedCount(input.readInt(), maximum, "string");
        Set<String> values = new LinkedHashSet<>();
        for (int index = 0; index < count; index++) {
            if (!values.add(readString(input))) {
                throw new LibraryUpdateException("Update state contains duplicate strings");
            }
        }
        return Set.copyOf(values);
    }

    private static void writeString(DataOutputStream output, String value) throws IOException {
        byte[] bytes = Objects.requireNonNull(value, "value must not be null")
                .getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAXIMUM_STRING_BYTES) {
            throw new LibraryUpdateException("Update state string exceeds the supported size");
        }
        output.writeInt(bytes.length);
        output.write(bytes);
    }

    private static String readString(DataInputStream input) throws IOException {
        int length = boundedCount(input.readInt(), MAXIMUM_STRING_BYTES, "string byte");
        byte[] bytes = input.readNBytes(length);
        if (bytes.length != length) {
            throw new EOFException("Truncated update state string");
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static int boundedCount(int count, int maximum, String name) {
        if (count < 0 || count > maximum) {
            throw new LibraryUpdateException("Invalid " + name + " count: " + count);
        }
        return count;
    }

    private static void validateSize(State state) {
        if (state.baselines().size() > MAXIMUM_BASELINES || state.events().size() > MAXIMUM_EVENTS) {
            throw new LibraryUpdateException("Library update state exceeds supported entry limits");
        }
        long contentCount = state.baselines().values().stream().mapToLong(Set::size).sum();
        if (contentCount > MAXIMUM_CONTENT_IDS) {
            throw new LibraryUpdateException("Library update baselines exceed the supported size");
        }
    }

    private static void moveAtomically(Path source, Path destination) throws IOException {
        try {
            Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            throw new IOException("Atomic update state replacement is unavailable", exception);
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

    record State(
            LibraryUpdatePolicy policy,
            Map<LibraryItemId, Set<String>> baselines,
            List<LibraryUpdateEvent> events,
            Optional<Instant> lastRunAt) {
        State {
            Objects.requireNonNull(policy, "policy must not be null");
            Map<LibraryItemId, Set<String>> baselineCopy = new LinkedHashMap<>();
            baselines.forEach((itemId, contentIds) -> baselineCopy.put(itemId, Set.copyOf(contentIds)));
            baselines = Map.copyOf(baselineCopy);
            events = events.stream().sorted(EVENT_ORDER).toList();
            lastRunAt = Objects.requireNonNull(lastRunAt, "lastRunAt must not be null");
        }

        static State empty() {
            return new State(LibraryUpdatePolicy.defaults(), Map.of(), List.of(), Optional.empty());
        }
    }

    private record EventKey(LibraryItemId itemId, String contentId) {
        private static EventKey of(LibraryUpdateEvent event) {
            return new EventKey(event.libraryItemId(), event.sourceContentId());
        }
    }
}
