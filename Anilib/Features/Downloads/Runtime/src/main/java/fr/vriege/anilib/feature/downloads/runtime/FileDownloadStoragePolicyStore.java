package fr.vriege.anilib.feature.downloads.runtime;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.OptionalLong;

final class FileDownloadStoragePolicyStore {
    private static final String HEADER = "ANILIB_DOWNLOAD_STORAGE_POLICY\t1\t";
    private final Path file;

    FileDownloadStoragePolicyStore(Path file) {
        this.file = file.toAbsolutePath().normalize();
    }

    OptionalLong load() throws IOException {
        if (!Files.exists(file)) {
            return OptionalLong.empty();
        }
        String value = Files.readString(file, StandardCharsets.UTF_8).strip();
        if (!value.startsWith(HEADER)) {
            throw new IOException("Unsupported download storage policy format");
        }
        try {
            long maximumStorageBytes = Long.parseLong(value.substring(HEADER.length()));
            if (maximumStorageBytes < 1L) {
                throw new NumberFormatException("storage limit must be positive");
            }
            return OptionalLong.of(maximumStorageBytes);
        } catch (NumberFormatException exception) {
            throw new IOException("Invalid download storage policy", exception);
        }
    }

    void save(long maximumStorageBytes) throws IOException {
        Files.createDirectories(file.getParent());
        Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(
                temporary,
                HEADER + maximumStorageBytes,
                StandardCharsets.UTF_8);
        try {
            Files.move(
                    temporary,
                    file,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
