package fr.vriege.anilib.tooling.archtests;

import fr.vriege.anilib.feature.library.ui.LibraryUiCapabilities;
import fr.vriege.anilib.platform.android.AndroidProductHost;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

/** Verifies the SDK-free Android product lifecycle adapter. */
final class AndroidProductHostTest {
    private AndroidProductHostTest() {
    }

    static int run() {
        Path dataDirectory;
        try {
            dataDirectory = Files.createTempDirectory("anilib-android-host-test");
        } catch (IOException exception) {
            throw new AssertionError("Unable to create Android host test directory", exception);
        }

        int assertions = 0;
        try (AndroidProductHost host = new AndroidProductHost(dataDirectory)) {
            assertions += expectIllegalState(host::componentCount);
            host.start();
            assertions += check(host.componentCount() == 2, "Android host must expose the standard bundles");
            assertions += check(
                    host.capability(LibraryUiCapabilities.PRESENTATION).library().titles().isEmpty(),
                    "Android host must expose the shared Library presentation");
            assertions += expectIllegalState(host::start);
            host.stop();
            host.stop();
            assertions += expectIllegalState(
                    () -> host.capability(LibraryUiCapabilities.PRESENTATION));
        } finally {
            deleteDirectory(dataDirectory);
        }
        return assertions;
    }

    private static int check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
        return 1;
    }

    private static int expectIllegalState(Runnable action) {
        try {
            action.run();
            throw new AssertionError("Expected Android host lifecycle failure");
        } catch (IllegalStateException expected) {
            return 1;
        }
    }

    private static void deleteDirectory(Path directory) {
        try (java.util.stream.Stream<Path> paths = Files.walk(directory)) {
            paths.sorted(Comparator.reverseOrder()).forEach(AndroidProductHostTest::delete);
        } catch (IOException exception) {
            throw new AssertionError("Unable to clean Android host test directory", exception);
        }
    }

    private static void delete(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException exception) {
            throw new UncheckedIOException("Unable to delete " + path, exception);
        }
    }
}
