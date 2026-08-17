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
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.Objects;
import java.util.Optional;

/** File-backed encoded cover cache decoded through the standard JDK image readers. */
public final class JdkFileCoverCache implements CoverCache {
    private static final int MAX_ENCODED_BYTES = 16 * 1024 * 1024;
    private static final long MAX_PIXELS = 16L * 1024L * 1024L;
    private static final HexFormat HEX = HexFormat.of();

    private final Path root;

    public JdkFileCoverCache(Path root) {
        this.root = Objects.requireNonNull(root, "root must not be null").toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.root);
            if (Files.isSymbolicLink(this.root) || !Files.isDirectory(this.root, LinkOption.NOFOLLOW_LINKS)) {
                throw new CoverCacheException("Cover cache root must be a real directory");
            }
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
                return decode(readEncoded(cacheFile));
            } catch (CoverCacheException invalidCache) {
                delete(cacheFile, "discard invalid cached cover");
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
        return decoded;
    }

    @Override
    public synchronized Optional<DecodedImage> find(CoverKey key) {
        Path cacheFile = cacheFile(key);
        if (!Files.exists(cacheFile, LinkOption.NOFOLLOW_LINKS)) {
            return Optional.empty();
        }
        return Optional.of(decode(readEncoded(cacheFile)));
    }

    @Override
    public synchronized void invalidate(CoverKey key) {
        delete(cacheFile(key), "invalidate cached cover");
    }

    private Path cacheFile(CoverKey key) {
        Objects.requireNonNull(key, "key must not be null");
        return root.resolve(digest(key.value()) + ".image");
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
