package fr.vriege.anilib.feature.reader.runtime;

import fr.vriege.anilib.feature.library.LibraryItemId;
import fr.vriege.anilib.feature.reader.ReaderReadEvent;
import fr.vriege.anilib.feature.reader.ReaderReadStateStore;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Base64;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public final class FileReaderReadStateStore implements ReaderReadStateStore {
    private final Path file;
    private final CopyOnWriteArrayList<Consumer<ReaderReadEvent>> listeners = new CopyOnWriteArrayList<>();

    public FileReaderReadStateStore(Path file) {
        this.file = Objects.requireNonNull(file, "file must not be null").toAbsolutePath().normalize();
    }

    @Override
    public synchronized Set<String> readContentIds(LibraryItemId libraryItemId) {
        Objects.requireNonNull(libraryItemId, "libraryItemId must not be null");
        return entries().stream()
                .filter(entry -> entry.libraryItemId().equals(libraryItemId.value()))
                .map(Entry::contentId)
                .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public synchronized void setRead(LibraryItemId libraryItemId, String contentId, boolean read) {
        setRead(libraryItemId, Set.of(contentId), read);
    }

    @Override
    public synchronized void setRead(
            LibraryItemId libraryItemId,
            Collection<String> contentIds,
            boolean read) {
        Objects.requireNonNull(libraryItemId, "libraryItemId must not be null");
        Set<String> selected = Set.copyOf(Objects.requireNonNull(
                contentIds,
                "contentIds must not be null"));
        if (selected.stream().anyMatch(String::isBlank)) {
            throw new IllegalArgumentException("contentIds must not contain blank values");
        }
        Set<Entry> entries = entries();
        if (read) {
            selected.forEach(contentId -> entries.add(new Entry(libraryItemId.value(), contentId)));
        } else {
            entries.removeIf(entry -> entry.libraryItemId().equals(libraryItemId.value())
                    && selected.contains(entry.contentId()));
        }
        if (!selected.isEmpty()) {
            write(entries);
            ReaderReadEvent event = new ReaderReadEvent(libraryItemId, selected, read);
            listeners.forEach(listener -> listener.accept(event));
        }
    }

    @Override
    public AutoCloseable observe(Consumer<ReaderReadEvent> listener) {
        Consumer<ReaderReadEvent> value = Objects.requireNonNull(listener, "listener must not be null");
        listeners.add(value);
        return () -> listeners.remove(value);
    }

    private Set<Entry> entries() {
        Set<Entry> entries = new LinkedHashSet<>();
        if (!Files.exists(file)) {
            return entries;
        }
        try {
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                String[] columns = line.split("=", -1);
                Entry entry = columns.length == 2
                        ? new Entry(decode(columns[0]), decode(columns[1]))
                        : null;
                if (entry == null || !entries.add(entry)) {
                    throw new IllegalStateException("Invalid reader read-state row");
                }
            }
            return entries;
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("Invalid reader read-state value", exception);
        } catch (IOException exception) {
            throw new UncheckedIOException("Unable to read reader read state", exception);
        }
    }

    private void write(Set<Entry> entries) {
        Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        try {
            Files.createDirectories(file.getParent());
            Files.write(
                    temporary,
                    entries.stream()
                            .sorted(Comparator.comparing(Entry::libraryItemId).thenComparing(Entry::contentId))
                            .map(entry -> encode(entry.libraryItemId()) + "=" + encode(entry.contentId()))
                            .toList(),
                    StandardCharsets.UTF_8);
            moveAtomically(temporary, file);
        } catch (IOException exception) {
            throw new UncheckedIOException("Unable to write reader read state", exception);
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

    private record Entry(String libraryItemId, String contentId) {
        private Entry {
            if (libraryItemId.isBlank() || contentId.isBlank()) {
                throw new IllegalArgumentException("reader read-state values must not be blank");
            }
        }
    }
}
