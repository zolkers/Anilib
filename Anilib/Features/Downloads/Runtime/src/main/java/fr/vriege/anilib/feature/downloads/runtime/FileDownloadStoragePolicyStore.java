package fr.vriege.anilib.feature.downloads.runtime;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;

final class FileDownloadStoragePolicyStore {
    private static final String VERSION_ONE_HEADER = "ANILIB_DOWNLOAD_STORAGE_POLICY\t1\t";
    private static final String VERSION_TWO_HEADER = "ANILIB_DOWNLOAD_STORAGE_POLICY\t2\t";
    private final Path file;

    FileDownloadStoragePolicyStore(Path file) {
        this.file = file.toAbsolutePath().normalize();
    }

    Optional<StoredPolicy> load(int fallbackConcurrentJobs) throws IOException {
        if (!Files.exists(file)) {
            return Optional.empty();
        }
        String value = Files.readString(file, StandardCharsets.UTF_8).strip();
        try {
            long maximumStorageBytes;
            int concurrentJobs;
            if (value.startsWith(VERSION_TWO_HEADER)) {
                String[] fields = value.substring(VERSION_TWO_HEADER.length()).split("\\t", -1);
                if (fields.length != 2) {
                    throw new NumberFormatException("expected storage and concurrency fields");
                }
                maximumStorageBytes = Long.parseLong(fields[0]);
                concurrentJobs = Integer.parseInt(fields[1]);
            } else if (value.startsWith(VERSION_ONE_HEADER)) {
                maximumStorageBytes = Long.parseLong(value.substring(VERSION_ONE_HEADER.length()));
                concurrentJobs = fallbackConcurrentJobs;
            } else {
                throw new IOException("Unsupported download storage policy format");
            }
            if (maximumStorageBytes < 1L) {
                throw new NumberFormatException("storage limit must be positive");
            }
            return Optional.of(new StoredPolicy(maximumStorageBytes, concurrentJobs));
        } catch (NumberFormatException exception) {
            throw new IOException("Invalid download storage policy", exception);
        }
    }

    void save(long maximumStorageBytes, int concurrentJobs) throws IOException {
        Files.createDirectories(file.getParent());
        Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(
                temporary,
                VERSION_TWO_HEADER + maximumStorageBytes + "\t" + concurrentJobs,
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

    record StoredPolicy(long maximumStorageBytes, int concurrentJobs) {
    }
}
