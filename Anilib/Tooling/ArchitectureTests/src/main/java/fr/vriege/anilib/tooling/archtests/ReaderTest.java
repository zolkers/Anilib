package fr.vriege.anilib.tooling.archtests;

import fr.vriege.anilib.configuration.standard.StandardAnilib;
import fr.vriege.anilib.feature.library.LibraryCapabilities;
import fr.vriege.anilib.feature.library.LibraryCatalog;
import fr.vriege.anilib.feature.library.LibraryItem;
import fr.vriege.anilib.feature.library.LibraryItemId;
import fr.vriege.anilib.feature.library.LibraryOrigin;
import fr.vriege.anilib.feature.library.MediaKind;
import fr.vriege.anilib.feature.reader.ReaderCapabilities;
import fr.vriege.anilib.feature.reader.ReaderException;
import fr.vriege.anilib.feature.reader.ReaderPolicy;
import fr.vriege.anilib.feature.reader.ReaderService;
import fr.vriege.anilib.feature.reader.ReaderSession;
import fr.vriege.anilib.feature.reader.ReadingDirection;
import fr.vriege.anilib.feature.reader.runtime.DefaultReaderService;
import fr.vriege.anilib.feature.source.InstalledSourceExtension;
import fr.vriege.anilib.feature.source.PagedSource;
import fr.vriege.anilib.feature.source.Source;
import fr.vriege.anilib.feature.source.SourceCatalogueItemId;
import fr.vriege.anilib.feature.source.SourceContentKind;
import fr.vriege.anilib.feature.source.SourceContentUnit;
import fr.vriege.anilib.feature.source.SourceContentUnitId;
import fr.vriege.anilib.feature.source.SourceDescriptor;
import fr.vriege.anilib.feature.source.SourceId;
import fr.vriege.anilib.feature.source.SourcePageResource;
import fr.vriege.anilib.feature.source.SourceRegistry;
import fr.vriege.anilib.feature.source.SourceSdk;
import fr.vriege.anilib.kernel.StartedAnilib;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/** End-to-end reader, persistence, prefetch, ownership, and limit checks. */
final class ReaderTest {
    private static final byte[] FIRST_PAGE = {1, 2, 3};
    private static final byte[] SECOND_PAGE = {4, 5, 6};

    private ReaderTest() {
    }

    static int run() {
        Counter counter = new Counter();
        verifiesStandardLocalReader(counter);
        verifiesBoundedPipeline(counter);
        return counter.value;
    }

    private static void verifiesStandardLocalReader(Counter counter) {
        Path dataDirectory = null;
        LibraryItemId itemId;
        try {
            dataDirectory = Files.createTempDirectory("anilib-reader-standard");
            Path pages = Files.createDirectories(dataDirectory.resolve("local-content").resolve("Book"));
            Files.write(pages.resolve("001.png"), FIRST_PAGE);
            Files.write(pages.resolve("002.png"), SECOND_PAGE);

            try (StartedAnilib product = StandardAnilib.start(dataDirectory)) {
                LibraryCatalog library = product.capability(LibraryCapabilities.CATALOG);
                LibraryItem item = LibraryItem.create("Book", MediaKind.MANGA)
                        .withOrigin(new LibraryOrigin("anilib.local", "DIRECTORY:Book"));
                itemId = item.id();
                library.save(item);
                ReaderService reader = product.capability(ReaderCapabilities.SERVICE);
                counter.check(reader.canOpen(item.id()), "reader must recognize paged local library titles");
                try (ReaderSession session = reader.open(item.id())) {
                    counter.check(session.snapshot().pageCount() == 2,
                            "reader must expose every local page");
                    counter.check(Arrays.equals(session.currentPage(), FIRST_PAGE),
                            "reader must return the first source page");
                    for (ReadingDirection direction : ReadingDirection.values()) {
                        session.setDirection(direction);
                        counter.check(session.snapshot().direction() == direction,
                                "reader must retain every supported reading direction");
                    }
                    counter.check(session.nextPage(), "reader must advance before the final page");
                    counter.check(Arrays.equals(session.currentPage(), SECOND_PAGE),
                            "reader must return the advanced page");
                }
                LibraryItem saved = library.find(item.id()).orElseThrow();
                counter.check(saved.progress().orElseThrow().position() == 1,
                        "reader navigation must durably persist page progress");
                counter.check(saved.progress().orElseThrow().extent() == 1,
                        "reader progress must retain the chapter extent");
                counter.check(saved.history().size() == 1,
                        "opening a reader session must append one history entry");
            }

            try (StartedAnilib product = StandardAnilib.start(dataDirectory);
                    ReaderSession resumed = product.capability(ReaderCapabilities.SERVICE).open(itemId)) {
                counter.check(resumed.snapshot().currentPageIndex() == 1,
                        "reader must resume the persisted page after a product restart");
            }
        } catch (IOException exception) {
            throw new AssertionError("Unable to prepare local reader test", exception);
        } finally {
            deleteTree(dataDirectory);
        }
    }

    private static void verifiesBoundedPipeline(Counter counter) {
        SourceCatalogueItemId sourceItemId = new SourceCatalogueItemId(SourceId.of("test.reader"), "title");
        SourceContentUnit unit = new SourceContentUnit(
                new SourceContentUnitId(sourceItemId, "chapter-1"),
                "Chapter 1",
                Optional.of(Instant.EPOCH));
        AtomicInteger reads = new AtomicInteger();
        TestPagedSource source = new TestPagedSource(unit, reads);
        MemoryLibraryCatalog library = new MemoryLibraryCatalog();
        LibraryItem item = LibraryItem.create("Pipeline", MediaKind.MANGA)
                .withOrigin(new LibraryOrigin("test.reader", "title"));
        library.save(item);
        DefaultReaderService reader = new DefaultReaderService(
                new SingleSourceRegistry(source),
                library,
                new ReaderPolicy(1, 4, 3));
        ReaderSession session = reader.open(item.id());
        byte[] returned = session.currentPage();
        returned[0] = 99;
        counter.check(session.currentPage()[0] == FIRST_PAGE[0],
                "reader cache must retain ownership of source page bytes");
        awaitPrefetch(reads);
        counter.check(reads.get() >= 2,
                "reading one page must prefetch its configured neighbor");
        session.goToPage(2);
        counter.expectReaderFailure(session::currentPage,
                "reader must reject a page exceeding its configured size limit");
        counter.expectIllegalArgument(() -> session.page(3),
                "reader must reject page indexes outside the validated sequence");
        session.close();
        counter.expectReaderFailure(session::snapshot,
                "closed reader sessions must reject further access");
        reader.close();
        counter.expectReaderFailure(() -> reader.canOpen(item.id()),
                "closed reader services must reject further access");
    }

    private static void awaitPrefetch(AtomicInteger reads) {
        long deadline = System.nanoTime() + 1_000_000_000L;
        while (reads.get() < 2 && System.nanoTime() < deadline) {
            Thread.onSpinWait();
        }
    }

    private static void deleteTree(Path root) {
        if (root == null) {
            return;
        }
        try (java.util.stream.Stream<Path> paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        } catch (IOException exception) {
            throw new AssertionError("Unable to clean reader test directory", exception);
        }
    }

    private static final class TestPagedSource implements PagedSource {
        private final SourceContentUnit unit;
        private final AtomicInteger reads;
        private final List<byte[]> content = List.of(FIRST_PAGE, SECOND_PAGE, new byte[]{7, 8, 9, 10});

        private TestPagedSource(SourceContentUnit unit, AtomicInteger reads) {
            this.unit = unit;
            this.reads = reads;
        }

        @Override
        public SourceDescriptor descriptor() {
            return new SourceDescriptor(
                    SourceId.of("test.reader"),
                    "Test reader",
                    "1.0.0",
                    "und",
                    Set.of(SourceContentKind.MANGA),
                    SourceSdk.API_VERSION);
        }

        @Override
        public List<SourceContentUnit> contentUnits(SourceCatalogueItemId itemId) {
            return List.of(unit);
        }

        @Override
        public List<SourcePageResource> pages(SourceContentUnitId contentUnitId) {
            return List.of(
                    new SourcePageResource(contentUnitId, "0", 0, FIRST_PAGE.length),
                    new SourcePageResource(contentUnitId, "1", 1, SECOND_PAGE.length),
                    new SourcePageResource(contentUnitId, "2", 2, 4));
        }

        @Override
        public byte[] readPage(SourcePageResource page) {
            reads.incrementAndGet();
            return content.get(page.index());
        }
    }

    private static final class SingleSourceRegistry implements SourceRegistry {
        private final Source source;

        private SingleSourceRegistry(Source source) {
            this.source = source;
        }

        @Override
        public List<Source> sources() {
            return List.of(source);
        }

        @Override
        public List<InstalledSourceExtension> extensions() {
            return List.of();
        }

        @Override
        public Optional<Source> find(SourceId id) {
            return source.descriptor().id().equals(id) ? Optional.of(source) : Optional.empty();
        }
    }

    private static final class MemoryLibraryCatalog implements LibraryCatalog {
        private final Map<LibraryItemId, LibraryItem> items = new LinkedHashMap<>();

        @Override
        public synchronized List<LibraryItem> snapshot() {
            return List.copyOf(items.values());
        }

        @Override
        public synchronized Optional<LibraryItem> find(LibraryItemId id) {
            return Optional.ofNullable(items.get(id));
        }

        @Override
        public synchronized void save(LibraryItem item) {
            items.put(item.id(), item);
        }

        @Override
        public synchronized void replaceAll(java.util.Collection<LibraryItem> replacement) {
            items.clear();
            replacement.forEach(item -> items.put(item.id(), item));
        }

        @Override
        public synchronized boolean remove(LibraryItemId id) {
            return items.remove(id) != null;
        }
    }

    private static final class Counter {
        private int value;

        private void check(boolean condition, String message) {
            value++;
            if (!condition) {
                throw new AssertionError(message);
            }
        }

        private void expectReaderFailure(Runnable action, String message) {
            try {
                action.run();
                throw new AssertionError(message);
            } catch (ReaderException expected) {
                value++;
            }
        }

        private void expectIllegalArgument(Runnable action, String message) {
            try {
                action.run();
                throw new AssertionError(message);
            } catch (IllegalArgumentException expected) {
                value++;
            }
        }
    }
}
