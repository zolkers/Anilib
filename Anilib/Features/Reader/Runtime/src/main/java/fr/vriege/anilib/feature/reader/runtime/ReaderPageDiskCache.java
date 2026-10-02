package fr.vriege.anilib.feature.reader.runtime;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

final class ReaderPageDiskCache {
    private static final HexFormat HEX = HexFormat.of();
    private static final String FILE_SUFFIX = ".page";

    private final Path root;
    private final long maximumBytes;
    private final Map<String, Long> entries = new LinkedHashMap<>(64, 0.75f, true);
    private final boolean available;
    private long cachedBytes;

    ReaderPageDiskCache(Path root, long maximumBytes) {
        this.root = Objects.requireNonNull(root, "root must not be null").toAbsolutePath().normalize();
        if (maximumBytes <= 0) {
            throw new IllegalArgumentException("maximumBytes must be positive");
        }
        this.maximumBytes = maximumBytes;
        this.available = initialize();
    }

    synchronized byte[] get(String persistentId) {
        if (!available) {
            return null;
        }
        String fileName = fileName(persistentId);
        Long indexedSize = entries.get(fileName);
        if (indexedSize == null) {
            return null;
        }
        Path file = root.resolve(fileName);
        try {
            if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
                removeEntry(fileName, indexedSize);
                return null;
            }
            long size = Files.size(file);
            if (size <= 0 || size > maximumBytes || size != indexedSize) {
                discard(fileName, indexedSize, file);
                return null;
            }
            byte[] bytes = Files.readAllBytes(file);
            Files.setLastModifiedTime(file, FileTime.from(Instant.now()));
            return bytes;
        } catch (IOException exception) {
            discard(fileName, indexedSize, file);
            return null;
        }
    }

    synchronized void put(String persistentId, byte[] bytes) {
        if (!available || bytes.length == 0 || bytes.length > maximumBytes) {
            return;
        }
        String fileName = fileName(persistentId);
        Path destination = root.resolve(fileName);
        Path temporary = null;
        try {
            temporary = Files.createTempFile(root, ".reader-page-", ".tmp");
            try (FileChannel channel = FileChannel.open(
                    temporary,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING)) {
                ByteBuffer buffer = ByteBuffer.wrap(bytes);
                while (buffer.hasRemaining()) {
                    channel.write(buffer);
                }
                channel.force(true);
            }
            moveAtomically(temporary, destination);
            Long previous = entries.put(fileName, (long) bytes.length);
            if (previous != null) {
                cachedBytes -= previous;
            }
            cachedBytes += bytes.length;
            evictDown();
        } catch (IOException ignored) {
            // A cache failure must never make a readable page fail.
        } finally {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException ignored) {
                    // The dedicated cache directory is cleaned during the next initialization.
                }
            }
        }
    }

    private boolean initialize() {
        try {
            Files.createDirectories(root);
            if (Files.isSymbolicLink(root) || !Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
                return false;
            }
            try (Stream<Path> files = Files.list(root)) {
                for (Path file : files.sorted(Comparator.comparing(ReaderPageDiskCache::lastModified)).toList()) {
                    String name = file.getFileName().toString();
                    if (name.startsWith(".reader-page-") && name.endsWith(".tmp")) {
                        Files.deleteIfExists(file);
                    } else if (name.endsWith(FILE_SUFFIX)
                            && Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
                        long size = Files.size(file);
                        if (size > 0 && size <= maximumBytes) {
                            entries.put(name, size);
                            cachedBytes += size;
                        } else {
                            Files.deleteIfExists(file);
                        }
                    }
                }
            }
            evictDown();
            return true;
        } catch (IOException exception) {
            entries.clear();
            cachedBytes = 0L;
            return false;
        }
    }

    private void evictDown() {
        var iterator = entries.entrySet().iterator();
        while (cachedBytes > maximumBytes && iterator.hasNext()) {
            Map.Entry<String, Long> oldest = iterator.next();
            try {
                Files.deleteIfExists(root.resolve(oldest.getKey()));
            } catch (IOException ignored) {
                // Removing the index entry still prevents a broken cache file from being reused.
            }
            cachedBytes -= oldest.getValue();
            iterator.remove();
        }
    }

    private void discard(String fileName, long size, Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException ignored) {
            // The invalid entry is removed from the in-memory index either way.
        }
        removeEntry(fileName, size);
    }

    private void removeEntry(String fileName, long size) {
        entries.remove(fileName);
        cachedBytes -= size;
    }

    private static FileTime lastModified(Path path) {
        try {
            return Files.getLastModifiedTime(path, LinkOption.NOFOLLOW_LINKS);
        } catch (IOException ignored) {
            return FileTime.fromMillis(0L);
        }
    }

    private static String fileName(String persistentId) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encoded = persistentId.getBytes(StandardCharsets.UTF_8);
            return HEX.formatHex(digest.digest(encoded)) + FILE_SUFFIX;
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JDK does not provide SHA-256", exception);
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
