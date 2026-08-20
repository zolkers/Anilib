package fr.vriege.anilib.feature.extensionrepository.runtime;

import fr.vriege.anilib.feature.extensionrepository.ExtensionBrowsePreferenceStore;
import fr.vriege.anilib.feature.extensionrepository.ExtensionBrowsePreferences;
import fr.vriege.anilib.foundation.validation.Preconditions;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

public final class FileExtensionBrowsePreferenceStore implements ExtensionBrowsePreferenceStore {
    private static final int MAX_VALUES = 10_000;
    private final Path file;

    public FileExtensionBrowsePreferenceStore(Path file) {
        this.file = Preconditions.requireNonNull(file, "file").toAbsolutePath().normalize();
    }

    @Override
    public synchronized ExtensionBrowsePreferences snapshot() {
        if (!Files.exists(file)) {
            return ExtensionBrowsePreferences.defaults();
        }
        Set<String> languages = new LinkedHashSet<>();
        Set<String> pinned = new LinkedHashSet<>();
        try {
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                if (line.isBlank()) {
                    continue;
                }
                String[] columns = line.split("\\t", -1);
                if (columns.length != 2) {
                    throw new IllegalStateException("Invalid extension browse preference row");
                }
                String value = decode(columns[1]);
                Set<String> target = switch (columns[0]) {
                    case "language" -> languages;
                    case "pinned" -> pinned;
                    default -> throw new IllegalStateException("Unknown extension browse preference row");
                };
                if (!target.add(value)) {
                    throw new IllegalStateException("Duplicate extension browse preference");
                }
                if (languages.size() + pinned.size() > MAX_VALUES) {
                    throw new IllegalStateException("Too many extension browse preferences");
                }
            }
            return new ExtensionBrowsePreferences(languages, pinned);
        } catch (IOException exception) {
            throw new UncheckedIOException("Unable to read extension browse preferences", exception);
        }
    }

    @Override
    public synchronized void save(ExtensionBrowsePreferences preferences) {
        ExtensionBrowsePreferences value = Preconditions.requireNonNull(preferences, "preferences");
        if (value.enabledLanguages().size() + value.pinnedPackages().size() > MAX_VALUES) {
            throw new IllegalArgumentException("Too many extension browse preferences");
        }
        List<String> lines = Stream.concat(
                        value.enabledLanguages().stream().sorted().map(item -> "language\t" + encode(item)),
                        value.pinnedPackages().stream().sorted().map(item -> "pinned\t" + encode(item)))
                .toList();
        Path parent = file.getParent();
        Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        try {
            Files.createDirectories(parent);
            Files.write(temporary, lines, StandardCharsets.UTF_8);
            moveAtomically(temporary, file);
        } catch (IOException exception) {
            throw new UncheckedIOException("Unable to write extension browse preferences", exception);
        } finally {
            try {
                Files.deleteIfExists(temporary);
            } catch (IOException ignored) {
            }
        }
    }

    private static String encode(String value) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String decode(String value) {
        try {
            return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("Invalid extension browse preference encoding", exception);
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
