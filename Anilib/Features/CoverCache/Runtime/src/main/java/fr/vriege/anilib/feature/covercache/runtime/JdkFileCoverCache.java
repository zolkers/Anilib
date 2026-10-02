package fr.vriege.anilib.feature.covercache.runtime;

import fr.vriege.anilib.feature.covercache.CoverCache;
import fr.vriege.anilib.feature.covercache.CoverCacheException;
import fr.vriege.anilib.feature.covercache.CoverKey;
import fr.vriege.anilib.feature.covercache.CoverLoader;
import fr.vriege.anilib.feature.covercache.DecodedImage;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
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
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

public final class JdkFileCoverCache implements CoverCache {
    private static final int MAX_ENCODED_BYTES = 16 * 1024 * 1024;
    private static final long MAX_PIXELS = 16L * 1024L * 1024L;
    private static final long DEFAULT_MAXIMUM_CACHE_BYTES = 512L * 1024L * 1024L;
    private static final HexFormat HEX = HexFormat.of();

    private final Path root;
    private final long maximumCacheBytes;
    private final Map<Path, Long> entries = new LinkedHashMap<>(64, 0.75f, true);
    private long cachedBytes;

    public JdkFileCoverCache(Path root) {
        this(root, DEFAULT_MAXIMUM_CACHE_BYTES);
    }

    public JdkFileCoverCache(Path root, long maximumCacheBytes) {
        this.root = Objects.requireNonNull(root, "root must not be null").toAbsolutePath().normalize();
        if (maximumCacheBytes <= 0) {
            throw new IllegalArgumentException("maximumCacheBytes must be positive");
        }
        this.maximumCacheBytes = maximumCacheBytes;
        try {
            Files.createDirectories(this.root);
            if (Files.isSymbolicLink(this.root) || !Files.isDirectory(this.root, LinkOption.NOFOLLOW_LINKS)) {
                throw new CoverCacheException("Cover cache root must be a real directory");
            }
            indexExistingEntries();
        } catch (IOException exception) {
            throw failure("create cover cache root", exception);
        }
    }

    @Override
    public synchronized DecodedImage load(CoverKey key, CoverLoader loader) {
        Objects.requireNonNull(loader, "loader must not be null");
        Path cacheFile = cacheFile(key);
        if (Files.exists(cacheFile, LinkOption.NOFOLLOW_LINKS)) {
            try {
                DecodedImage cached = decode(readEncoded(cacheFile));
                touch(cacheFile);
                return cached;
            } catch (CoverCacheException invalidCache) {
                delete(cacheFile, "discard invalid cached cover");
                removeEntry(cacheFile);
            }
        }

        byte[] encoded;
        try {
            encoded = Objects.requireNonNull(loader.load(), "loader result must not be null");
        } catch (IOException exception) {
            throw failure("load cover bytes", exception);
        }
        requireEncodedSize(encoded.length);
        DecodedImage decoded = decode(encoded);
        store(cacheFile, encoded);
        recordStored(cacheFile, encoded.length);
        return decoded;
    }

    @Override
    public synchronized Optional<DecodedImage> find(CoverKey key) {
        Path cacheFile = cacheFile(key);
        if (!Files.exists(cacheFile, LinkOption.NOFOLLOW_LINKS)) {
            return Optional.empty();
        }
        DecodedImage decoded = decode(readEncoded(cacheFile));
        touch(cacheFile);
        return Optional.of(decoded);
    }

    @Override
    public synchronized void invalidate(CoverKey key) {
        Path file = cacheFile(key);
        delete(file, "invalidate cached cover");
        removeEntry(file);
    }

    private Path cacheFile(CoverKey key) {
        Objects.requireNonNull(key, "key must not be null");
        return root.resolve(digest(key.value()) + ".image");
    }

    private void indexExistingEntries() throws IOException {
        try (Stream<Path> files = Files.list(root)) {
            for (Path file : files
                    .filter(path -> path.getFileName().toString().endsWith(".image"))
                    .sorted(Comparator.comparing(JdkFileCoverCache::lastModified))
                    .toList()) {
                if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
                    continue;
                }
                long size = Files.size(file);
                if (size <= 0 || size > MAX_ENCODED_BYTES) {
                    Files.deleteIfExists(file);
                    continue;
                }
                entries.put(file, size);
                cachedBytes += size;
            }
        }
        evictDown();
    }

    private void touch(Path file) {
        entries.get(file);
        try {
            Files.setLastModifiedTime(file, FileTime.from(Instant.now()));
        } catch (IOException ignored) {
            // The in-process LRU order remains valid even when metadata cannot be updated.
        }
    }

    private void recordStored(Path file, long size) {
        Long previous = entries.put(file, size);
        if (previous != null) {
            cachedBytes -= previous;
        }
        cachedBytes += size;
        evictDown();
    }

    private void removeEntry(Path file) {
        Long removed = entries.remove(file);
        if (removed != null) {
            cachedBytes -= removed;
        }
    }

    private void evictDown() {
        Iterator<Map.Entry<Path, Long>> iterator = entries.entrySet().iterator();
        while (cachedBytes > maximumCacheBytes && iterator.hasNext()) {
            Map.Entry<Path, Long> oldest = iterator.next();
            try {
                Files.deleteIfExists(oldest.getKey());
            } catch (IOException ignored) {
                // Removing the index entry prevents a failed eviction from blocking newer covers.
            }
            cachedBytes -= oldest.getValue();
            iterator.remove();
        }
    }

    private static FileTime lastModified(Path path) {
        try {
            return Files.getLastModifiedTime(path, LinkOption.NOFOLLOW_LINKS);
        } catch (IOException ignored) {
            return FileTime.fromMillis(0L);
        }
    }

    private static String digest(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HEX.formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JDK does not provide SHA-256", exception);
        }
    }

    private static DecodedImage decode(byte[] encoded) {
        requireEncodedSize(encoded.length);
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(encoded))) {
            if (input == null) {
                throw new CoverCacheException("Unable to create an image input stream");
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw new CoverCacheException("Cover format is not supported by the JDK");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, false, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                requireDimensions(width, height);
                BufferedImage image = reader.read(0);
                requireDimensions(image.getWidth(), image.getHeight());
                int[] pixels = image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
                return new DecodedImage(image.getWidth(), image.getHeight(), pixels);
            } finally {
                reader.dispose();
            }
        } catch (IOException exception) {
            throw failure("decode cover image", exception);
        }
    }

    private static void requireDimensions(int width, int height) {
        long pixels = (long) width * height;
        if (width <= 0 || height <= 0 || pixels > MAX_PIXELS) {
            throw new CoverCacheException("Cover dimensions exceed the safe pixel limit");
        }
    }

    private static void requireEncodedSize(int size) {
        if (size == 0) {
            throw new CoverCacheException("Cover image is empty");
        }
        if (size > MAX_ENCODED_BYTES) {
            throw new CoverCacheException("Encoded cover exceeds the size limit");
        }
    }

    private static byte[] readEncoded(Path file) {
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
            throw new CoverCacheException("Cached cover is not a regular file");
        }
        try {
            long size = Files.size(file);
            if (size <= 0 || size > MAX_ENCODED_BYTES) {
                throw new CoverCacheException("Cached cover has an invalid size");
            }
            try (InputStream input = Files.newInputStream(file)) {
                byte[] encoded = input.readNBytes(MAX_ENCODED_BYTES + 1);
                requireEncodedSize(encoded.length);
                return encoded;
            }
        } catch (IOException exception) {
            throw failure("read cached cover", exception);
        }
    }

    private static void store(Path destination, byte[] encoded) {
        Path temporary;
        try {
            temporary = Files.createTempFile(destination.getParent(), ".cover-", ".tmp");
        } catch (IOException exception) {
            throw failure("create temporary cover file", exception);
        }
        try {
            try (FileChannel channel = FileChannel.open(
                    temporary,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING)) {
                ByteBuffer buffer = ByteBuffer.wrap(encoded);
                while (buffer.hasRemaining()) {
                    channel.write(buffer);
                }
                channel.force(true);
            }
            moveAtomically(temporary, destination);
        } catch (IOException exception) {
            throw failure("store cached cover", exception);
        } finally {
            try {
                Files.deleteIfExists(temporary);
            } catch (IOException ignored) {
                // The primary cache operation already reports the actionable failure.
            }
        }
    }

    private static void moveAtomically(Path source, Path destination) throws IOException {
        try {
            Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void delete(Path file, String operation) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException exception) {
            throw failure(operation, exception);
        }
    }

    private static CoverCacheException failure(String operation, IOException cause) {
        return new CoverCacheException("Unable to " + operation, cause);
    }
}
