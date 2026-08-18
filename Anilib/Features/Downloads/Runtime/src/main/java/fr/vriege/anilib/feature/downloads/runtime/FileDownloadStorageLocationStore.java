package fr.vriege.anilib.feature.downloads.runtime;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Base64;
import java.util.Optional;

final class FileDownloadStorageLocationStore {
    private static final String HEADER = "ANILIB_DOWNLOAD_LOCATION\t1\t";
    private final Path file;

    FileDownloadStorageLocationStore(Path file) {
        this.file = file.toAbsolutePath().normalize();
    }

    Optional<Path> load() throws IOException {
        if (!Files.exists(file)) {
            return Optional.empty();
        }
        String value = Files.readString(file, StandardCharsets.UTF_8).strip();
        if (!value.startsWith(HEADER)) {
            throw new IOException("Unsupported download storage location format");
        }
        try {
            String decoded = new String(
                    Base64.getUrlDecoder().decode(value.substring(HEADER.length())),
                    StandardCharsets.UTF_8);
            return Optional.of(Path.of(decoded).toAbsolutePath().normalize());
        } catch (IllegalArgumentException exception) {
            throw new IOException("Invalid download storage location", exception);
        }
    }

    void save(Path location) throws IOException {
        Files.createDirectories(file.getParent());
        String encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(
                location.toString().getBytes(StandardCharsets.UTF_8));
        Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(temporary, HEADER + encoded, StandardCharsets.UTF_8);
        try {
            Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
