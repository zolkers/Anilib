package fr.vriege.anilib.feature.discovery.runtime;

import fr.vriege.anilib.feature.discovery.DiscoveryBrowsePreferenceStore;
import fr.vriege.anilib.feature.discovery.DiscoveryBrowsePreferences;
import fr.vriege.anilib.feature.discovery.DiscoveryCatalogueDisplayMode;
import fr.vriege.anilib.feature.source.SourceContentKind;
import fr.vriege.anilib.feature.source.SourceId;
import fr.vriege.anilib.foundation.validation.Preconditions;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Base64;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

public final class FileDiscoveryBrowsePreferenceStore implements DiscoveryBrowsePreferenceStore {
    private static final int MAX_VALUES = 10_000;
    private final Path file;

    public FileDiscoveryBrowsePreferenceStore(Path file) {
        this.file = Preconditions.requireNonNull(file, "file").toAbsolutePath().normalize();
    }

    @Override
    public synchronized DiscoveryBrowsePreferences snapshot() {
        if (!Files.exists(file)) {
            return DiscoveryBrowsePreferences.defaults();
        }
        Map<SourceContentKind, Set<String>> languages = new EnumMap<>(SourceContentKind.class);
        Set<SourceId> pinned = new LinkedHashSet<>();
        Map<SourceId, DiscoveryCatalogueDisplayMode> displayModes = new LinkedHashMap<>();
        Set<SourceId> disabled = new LinkedHashSet<>();
        int values = 0;
        try {
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                if (line.isBlank()) {
                    continue;
                }
                String[] columns = line.split("\\t", -1);
                if (columns.length == 3 && columns[0].equals("language")) {
                    SourceContentKind kind = SourceContentKind.valueOf(columns[1]);
                    if (!languages.computeIfAbsent(kind, ignored -> new LinkedHashSet<>()).add(decode(columns[2]))) {
                        throw new IllegalStateException("Duplicate discovery language preference");
                    }
                } else if (columns.length == 2 && columns[0].equals("pinned")) {
                    if (!pinned.add(SourceId.of(decode(columns[1])))) {
                        throw new IllegalStateException("Duplicate pinned discovery source");
                    }
                } else if (columns.length == 3 && columns[0].equals("display")) {
                    SourceId sourceId = SourceId.of(decode(columns[1]));
                    DiscoveryCatalogueDisplayMode previous = displayModes.put(
                            sourceId,
                            DiscoveryCatalogueDisplayMode.valueOf(columns[2]));
                    if (previous != null) {
                        throw new IllegalStateException("Duplicate catalogue display preference");
                    }
                } else if (columns.length == 2 && columns[0].equals("disabled")) {
                    if (!disabled.add(SourceId.of(decode(columns[1])))) {
                        throw new IllegalStateException("Duplicate disabled discovery source");
                    }
                } else {
                    throw new IllegalStateException("Invalid discovery browse preference row");
                }
                values++;
                if (values > MAX_VALUES) {
                    throw new IllegalStateException("Too many discovery browse preferences");
                }
            }
            return new DiscoveryBrowsePreferences(languages, pinned, displayModes, disabled);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("Invalid discovery browse preference value", exception);
        } catch (IOException exception) {
            throw new UncheckedIOException("Unable to read discovery browse preferences", exception);
        }
    }

    @Override
    public synchronized void save(DiscoveryBrowsePreferences preferences) {
        DiscoveryBrowsePreferences value = Preconditions.requireNonNull(preferences, "preferences");
        int count = value.pinnedSources().size()
                + value.enabledLanguages().values().stream().mapToInt(Set::size).sum()
                + value.catalogueDisplayModes().size()
                + value.disabledSources().size();
        if (count > MAX_VALUES) {
            throw new IllegalArgumentException("Too many discovery browse preferences");
        }
        Stream<String> languageRows = value.enabledLanguages().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .flatMap(entry -> entry.getValue().stream()
                        .sorted()
                        .map(language -> "language\t" + entry.getKey().name() + "\t" + encode(language)));
        Stream<String> pinnedRows = value.pinnedSources().stream()
                .map(SourceId::toString)
                .sorted()
                .map(source -> "pinned\t" + encode(source));
        Stream<String> displayRows = value.catalogueDisplayModes().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> "display\t" + encode(entry.getKey().toString()) + "\t" + entry.getValue().name());
        Stream<String> disabledRows = value.disabledSources().stream()
                .map(SourceId::toString)
                .sorted()
                .map(source -> "disabled\t" + encode(source));
        Stream<String> visiblePreferences = Stream.concat(
                Stream.concat(languageRows, pinnedRows),
                displayRows);
        write(Stream.concat(visiblePreferences, disabledRows).toList());
    }

    private void write(List<String> lines) {
        Path parent = file.getParent();
        Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        try {
            Files.createDirectories(parent);
            Files.write(temporary, lines, StandardCharsets.UTF_8);
            moveAtomically(temporary, file);
        } catch (IOException exception) {
            throw new UncheckedIOException("Unable to write discovery browse preferences", exception);
        } finally {
            try {
                Files.deleteIfExists(temporary);
            } catch (IOException ignored) {
                // The primary operation reports the actionable error.
            }
        }
    }

    private static String encode(String value) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
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
}
