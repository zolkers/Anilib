package fr.vriege.anilib.tooling.archtests;

import fr.vriege.anilib.configuration.standard.StandardAnilib;
import fr.vriege.anilib.feature.covercache.CoverCache;
import fr.vriege.anilib.feature.covercache.CoverCacheCapabilities;
import fr.vriege.anilib.feature.covercache.CoverCacheException;
import fr.vriege.anilib.feature.covercache.CoverKey;
import fr.vriege.anilib.feature.covercache.DecodedImage;
import fr.vriege.anilib.feature.covercache.bundle.CoverCachePlugin;
import fr.vriege.anilib.feature.covercache.runtime.JdkFileCoverCache;
import fr.vriege.anilib.kernel.StartedAnilib;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/** Black-box checks for JDK image decoding and durable cover caching. */
final class CoverCacheTest {
    private static int assertions;

    private CoverCacheTest() {
    }

    static int run() {
        Path directory = temporaryDirectory();
        try {
            byte[] encoded = encodedPng();
            Path cacheDirectory = directory.resolve("covers");
            CoverCache cache = new JdkFileCoverCache(cacheDirectory);
            CoverKey key = new CoverKey("library/title:primary");
            int[] loads = {0};

            DecodedImage first = cache.load(key, () -> {
                loads[0]++;
                return encoded;
            });
            check(first.width() == 2 && first.height() == 2, "PNG dimensions must be decoded");
            check(first.argbAt(0, 0) == 0xFFFF0000, "PNG ARGB pixels must be preserved");
            check(loads[0] == 1, "first cache access must invoke the loader once");

            int[] exposedPixels = first.argbPixels();
            exposedPixels[0] = 0;
            check(first.argbAt(0, 0) == 0xFFFF0000, "decoded pixels must be defensively copied");

            DecodedImage reused = cache.load(key, () -> {
                throw new IOException("cached cover should not invoke its loader");
            });
            check(reused.equals(first), "memory-independent cache reads must decode the same image");
            check(loads[0] == 1, "cache hit must not invoke the loader");

            CoverCache restarted = new JdkFileCoverCache(cacheDirectory);
            check(restarted.find(key).orElseThrow().equals(first), "cover must survive cache restart");

            Path storedCover = onlyEntry(cacheDirectory);
            check(storedCover.getFileName().toString().matches("[0-9a-f]{64}\\.image"),
                    "cover keys must map to fixed safe file names");
            write(storedCover, new byte[] {1, 2, 3});
            DecodedImage recovered = restarted.load(key, () -> {
                loads[0]++;
                return encoded;
            });
            check(recovered.equals(first), "invalid cached bytes must be replaced from the loader");
            check(loads[0] == 2, "invalid cached bytes must invoke the loader exactly once");

            CoverKey invalidKey = new CoverKey("invalid-image");
            expectFailure(() -> restarted.load(invalidKey, () -> new byte[] {4, 5, 6}));
            check(restarted.find(invalidKey).isEmpty(), "invalid image bytes must never be persisted");

            CoverKey hostileKey = new CoverKey("../../outside-cache");
            restarted.load(hostileKey, () -> encoded);
            try (Stream<Path> entries = Files.list(cacheDirectory)) {
                check(entries.allMatch(path -> path.normalize().getParent().equals(cacheDirectory.normalize())),
                        "hostile logical keys must remain inside the cache root");
            }
            restarted.invalidate(hostileKey);
            restarted.invalidate(key);
            check(restarted.find(key).isEmpty(), "invalidating a cover must remove its durable entry");

            Path productDirectory = directory.resolve("product");
            try (StartedAnilib application = StandardAnilib.start(
                    productDirectory,
                    List.of(new CoverCachePlugin(productDirectory.resolve("covers"))))) {
                check(application.components().size() == 9,
                        "platform plugin must extend the standard product explicitly");
                check(application.capability(CoverCacheCapabilities.CACHE).find(key).isEmpty(),
                        "cover cache Bundle must publish its capability");
            }
            return assertions;
        } catch (IOException exception) {
            throw new AssertionError("Unable to verify cover cache", exception);
        } finally {
            deleteDirectory(directory);
        }
    }

    private static byte[] encodedPng() {
        BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, 0xFFFF0000);
        image.setRGB(1, 0, 0xFF00FF00);
        image.setRGB(0, 1, 0xFF0000FF);
        image.setRGB(1, 1, 0x80FFFFFF);
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            if (!ImageIO.write(image, "png", output)) {
                throw new AssertionError("JDK PNG writer is unavailable");
            }
            return output.toByteArray();
        } catch (IOException exception) {
            throw new AssertionError("Unable to encode PNG test fixture", exception);
        }
    }

    private static Path temporaryDirectory() {
        try {
            return Files.createTempDirectory("anilib-cover-cache-test");
        } catch (IOException exception) {
            throw new AssertionError("Unable to create cover cache test directory", exception);
        }
    }

    private static Path onlyEntry(Path directory) throws IOException {
        try (Stream<Path> entries = Files.list(directory)) {
            List<Path> paths = entries.toList();
            check(paths.size() == 1, "one logical cover must create one durable cache file");
            return paths.getFirst();
        }
    }

    private static void write(Path file, byte[] bytes) throws IOException {
        Files.write(file, bytes);
    }

    private static void expectFailure(Action action) {
        try {
            action.run();
            throw new AssertionError("Expected invalid image bytes to be rejected");
        } catch (CoverCacheException expected) {
            assertions++;
        }
    }

    private static void deleteDirectory(Path directory) {
        if (!Files.exists(directory)) {
            return;
        }
        try (Stream<Path> entries = Files.walk(directory)) {
            for (Path entry : entries.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(entry);
            }
        } catch (IOException exception) {
            throw new AssertionError("Unable to clean cover cache test directory", exception);
        }
    }

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    @FunctionalInterface
    private interface Action {
        void run();
    }
}
