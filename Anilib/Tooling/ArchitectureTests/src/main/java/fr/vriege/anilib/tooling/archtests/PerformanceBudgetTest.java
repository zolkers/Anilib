package fr.vriege.anilib.tooling.archtests;

import fr.vriege.anilib.configuration.standard.StandardAnilib;
import fr.vriege.anilib.feature.library.LibraryItem;
import fr.vriege.anilib.feature.library.MediaKind;
import fr.vriege.anilib.feature.library.runtime.FileLibraryCatalog;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.stream.IntStream;

final class PerformanceBudgetTest {
    private static final int LARGE_LIBRARY_SIZE = 10_000;
    private static final Duration STARTUP_BUDGET = Duration.ofSeconds(20);
    private static final Duration LARGE_LIBRARY_WRITE_BUDGET = Duration.ofSeconds(20);
    private static final Duration LARGE_LIBRARY_REOPEN_BUDGET = Duration.ofSeconds(10);
    private static final long LARGE_LIBRARY_FILE_BUDGET = 32L * 1024L * 1024L;

    private PerformanceBudgetTest() {
    }

    static int run() {
        Path directory = temporaryDirectory();
        Counter counter = new Counter();
        try {
            long startup = measure(() -> {
                try (var ignored = StandardAnilib.start(directory.resolve("startup"))) {
                    counter.check(ignored.components().size() == 14,
                            "performance startup must construct the complete Standard product");
                }
            });
            counter.check(startup <= STARTUP_BUDGET.toNanos(),
                    "cold Standard startup exceeded " + STARTUP_BUDGET);

            Path file = directory.resolve("large-library.anilib");
            List<LibraryItem> items = IntStream.range(0, LARGE_LIBRARY_SIZE)
                    .mapToObj(index -> LibraryItem.create(
                            String.format("Performance title %05d", LARGE_LIBRARY_SIZE - index),
                            index % 2 == 0 ? MediaKind.MANGA : MediaKind.ANIME))
                    .toList();
            FileLibraryCatalog catalog = new FileLibraryCatalog(file);
            long write = measure(() -> catalog.replaceAll(items));
            counter.check(write <= LARGE_LIBRARY_WRITE_BUDGET.toNanos(),
                    "10,000-title atomic write exceeded " + LARGE_LIBRARY_WRITE_BUDGET);
            counter.check(size(file) <= LARGE_LIBRARY_FILE_BUDGET,
                    "10,000-title catalog exceeded its disk budget");

            Holder<FileLibraryCatalog> reopened = new Holder<>();
            long reopen = measure(() -> reopened.value = new FileLibraryCatalog(file));
            counter.check(reopen <= LARGE_LIBRARY_REOPEN_BUDGET.toNanos(),
                    "10,000-title reopen exceeded " + LARGE_LIBRARY_REOPEN_BUDGET);
            counter.check(reopened.value.snapshot().size() == LARGE_LIBRARY_SIZE
                            && reopened.value.snapshot().getFirst().title().equals("Performance title 00001"),
                    "large-library reopen and deterministic display sort must retain every title");
            return counter.value;
        } finally {
            deleteDirectory(directory);
        }
    }

    private static long measure(Runnable action) {
        long started = System.nanoTime();
        action.run();
        return System.nanoTime() - started;
    }

    private static long size(Path file) {
        try {
            return Files.size(file);
        } catch (IOException exception) {
            throw new AssertionError("Unable to inspect performance fixture size", exception);
        }
    }

    private static Path temporaryDirectory() {
        try {
            return Files.createTempDirectory("anilib-performance-budget");
        } catch (IOException exception) {
            throw new AssertionError("Unable to create performance fixture", exception);
        }
    }

    private static void deleteDirectory(Path directory) {
        try (var entries = Files.walk(directory)) {
            for (Path entry : entries.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(entry);
            }
        } catch (IOException exception) {
            throw new AssertionError("Unable to clean performance fixture", exception);
        }
    }

    private static final class Holder<T> {
        private T value;
    }

    private static final class Counter {
        private int value;

        private void check(boolean condition, String message) {
            value++;
            if (!condition) {
                throw new AssertionError(message);
            }
        }
    }
}
