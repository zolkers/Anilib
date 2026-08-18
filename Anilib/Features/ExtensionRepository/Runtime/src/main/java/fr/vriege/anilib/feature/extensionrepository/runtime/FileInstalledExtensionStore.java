package fr.vriege.anilib.feature.extensionrepository.runtime;

import fr.vriege.anilib.feature.extensionrepository.ExtensionArtifactFormat;
import fr.vriege.anilib.feature.extensionrepository.ExtensionInstallationState;
import fr.vriege.anilib.feature.extensionrepository.InstalledExtensionPackage;
import fr.vriege.anilib.foundation.validation.Preconditions;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Atomic metadata index for verified installed extension artifacts. */
public final class FileInstalledExtensionStore {
    private static final int FIELD_COUNT = 8;
    private static final int MAX_EXTENSIONS = 10_000;
    private final Path file;

    public FileInstalledExtensionStore(Path file) {
        this.file = Preconditions.requireNonNull(file, "file").toAbsolutePath().normalize();
    }

    public Map<String, InstalledExtensionPackage> load() {
        if (!Files.exists(file)) {
            return Map.of();
        }
        try {
            Map<String, InstalledExtensionPackage> installed = new LinkedHashMap<>();
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                if (line.isBlank()) {
                    continue;
                }
                String[] fields = line.split("\\t", -1);
                if (fields.length != FIELD_COUNT) {
                    throw new IllegalStateException("Malformed installed-extension entry");
                }
                InstalledExtensionPackage extension = decode(fields);
                if (installed.put(extension.packageName(), extension) != null
                        || installed.size() > MAX_EXTENSIONS) {
                    throw new IllegalStateException("Invalid installed-extension package set");
                }
            }
            return Map.copyOf(installed);
        } catch (IOException exception) {
            throw new UncheckedIOException("Unable to read installed extensions " + file, exception);
        }
    }

    public void save(Map<String, InstalledExtensionPackage> installed) {
        Map<String, InstalledExtensionPackage> values = Map.copyOf(
                Preconditions.requireNonNull(installed, "installed"));
        if (values.size() > MAX_EXTENSIONS) {
            throw new IllegalArgumentException("Installed extension count exceeds " + MAX_EXTENSIONS);
        }
        List<String> lines = new ArrayList<>();
        values.values().stream().sorted(Comparator.comparing(InstalledExtensionPackage::packageName))
                .map(FileInstalledExtensionStore::encode)
                .forEach(lines::add);
        writeAtomically(String.join(System.lineSeparator(), lines) + (lines.isEmpty() ? "" : System.lineSeparator()));
    }

    private static InstalledExtensionPackage decode(String[] fields) {
        try {
            return new InstalledExtensionPackage(
                    fields[0],
                    decodeText(fields[1]),
                    Long.parseLong(fields[2]),
                    decodeText(fields[3]),
                    ExtensionArtifactFormat.valueOf(fields[4]),
                    ExtensionInstallationState.valueOf(fields[5]),
                    fields[6],
                    Instant.parse(fields[7]));
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("Invalid installed-extension entry", exception);
        }
    }

    private static String encode(InstalledExtensionPackage extension) {
        return String.join(
                "\t",
                extension.packageName(),
                encodeText(extension.displayName()),
                Long.toString(extension.versionCode()),
                encodeText(extension.versionName()),
                extension.format().name(),
                extension.state().name(),
                extension.sha256(),
                extension.installedAt().toString());
    }

    private static String encodeText(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String decodeText(String value) {
        return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
    }

    private void writeAtomically(String content) {
        Path parent = file.getParent();
        Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        try {
            Files.createDirectories(parent);
            Files.writeString(temporary, content, StandardCharsets.UTF_8);
            try {
                Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            throw new UncheckedIOException("Unable to write installed extensions " + file, exception);
        }
    }
}
