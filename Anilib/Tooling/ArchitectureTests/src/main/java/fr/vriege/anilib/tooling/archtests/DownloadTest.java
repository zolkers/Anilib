package fr.vriege.anilib.tooling.archtests;

import fr.vriege.anilib.configuration.standard.StandardAnilib;
import fr.vriege.anilib.feature.downloads.DownloadCapabilities;
import fr.vriege.anilib.feature.downloads.DownloadException;
import fr.vriege.anilib.feature.downloads.DownloadId;
import fr.vriege.anilib.feature.downloads.DownloadJobSnapshot;
import fr.vriege.anilib.feature.downloads.DownloadIndexRepairResult;
import fr.vriege.anilib.feature.downloads.DownloadPriority;
import fr.vriege.anilib.feature.downloads.DownloadRecoveryMode;
import fr.vriege.anilib.feature.downloads.DownloadService;
import fr.vriege.anilib.feature.downloads.DownloadStatus;
import fr.vriege.anilib.feature.downloads.DownloadStoragePolicy;
import fr.vriege.anilib.feature.downloads.runtime.DefaultDownloadService;
import fr.vriege.anilib.feature.library.LibraryCapabilities;
import fr.vriege.anilib.feature.library.LibraryCatalog;
import fr.vriege.anilib.feature.library.LibraryItem;
import fr.vriege.anilib.feature.library.LibraryItemId;
import fr.vriege.anilib.feature.library.LibraryOrigin;
import fr.vriege.anilib.feature.library.MediaKind;
import fr.vriege.anilib.feature.reader.ReaderCapabilities;
import fr.vriege.anilib.feature.reader.ReaderService;
import fr.vriege.anilib.feature.reader.ReaderSession;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;

final class DownloadTest {
    private static final byte[] FIRST_PAGE = {11, 12, 13};
    private static final byte[] SECOND_PAGE = {21, 22, 23, 24};

    private DownloadTest() {
    }

    static int run() {
        Counter counter = new Counter();
        verifiesStandardOfflineReading(counter);
        verifiesResumableQueue(counter);
        verifiesPriorityMetricsAndRecovery(counter);
        verifiesStorageLimit(counter);
        enforcesLargeTransferPolicy(counter);
        cleansOrphanedDownloads(counter);
        return counter.value;
    }

    private static void verifiesPriorityMetricsAndRecovery(Counter counter) {
        Path queueRoot = null;
        Path recoveryRoot = null;
        Path customStorage = null;
        try {
            queueRoot = Files.createTempDirectory("anilib-download-priority");
            MemoryLibraryCatalog library = new MemoryLibraryCatalog();
            LibraryItem item = LibraryItem.create("Queue", MediaKind.MANGA)
                    .withOrigin(new LibraryOrigin("test.queue", "title"));
            library.save(item);
            QueuedPagedSource source = new QueuedPagedSource(false, true);
            DownloadStoragePolicy policy = new DownloadStoragePolicy(4096, 1024, 1, true, true);
            try (DefaultDownloadService downloads = new DefaultDownloadService(
                    new SingleSourceRegistry(source), library, queueRoot, policy)) {
                DownloadId first = downloads.enqueue(item.id(), source.unit("a").id());
                counter.check(source.awaitBlocked(), "priority test must block after its first persisted page");
                DownloadJobSnapshot active = await(downloads, first, job -> job.completedPages() == 1);
                counter.check(active.bytesPerSecond() > 0 && active.estimatedRemainingMillis().isPresent(),
                        "active downloads must expose measured speed and ETA");
                DownloadId second = downloads.enqueue(item.id(), source.unit("b").id());
                DownloadId third = downloads.enqueue(item.id(), source.unit("c").id());
                downloads.setPriority(third, DownloadPriority.HIGH);
                downloads.move(second, 0);
                DownloadJobSnapshot prioritized = downloads.snapshot().jobs().stream()
                        .filter(job -> job.id().equals(third))
                        .findFirst()
                        .orElseThrow();
                counter.check(prioritized.priority() == DownloadPriority.HIGH,
                        "download priorities must be visible in queue snapshots");
                source.release();
                await(downloads, first, job -> job.status() == DownloadStatus.COMPLETED);
                await(downloads, second, job -> job.status() == DownloadStatus.COMPLETED);
                await(downloads, third, job -> job.status() == DownloadStatus.COMPLETED);
                counter.check(source.completionOrder().equals(List.of("a", "c", "b")),
                        "high-priority queued work must run before manually reordered normal work");
                customStorage = Files.createTempDirectory("anilib-download-custom-storage");
                downloads.changeStorageLocation(customStorage);
                counter.check(downloads.storage().customLocation()
                                && downloads.storage().location().equals(customStorage.toAbsolutePath().normalize())
                                && downloads.storage().writable(),
                        "download storage migration must validate and expose the selected location");
                Path orphan = Files.createDirectories(
                        customStorage.resolve("content").resolve(DownloadId.create().toString()));
                Files.write(orphan.resolve("00000000.page"), new byte[] {9});
                DownloadIndexRepairResult repair = downloads.repairIndex();
                counter.check(repair.orphanedDirectoriesRemoved() == 1 && repair.indexedBytes() == 24L,
                        "download index repair must remove managed orphans and recount indexed bytes");
                downloads.removeTitle(item.id());
                counter.check(downloads.snapshot().jobs().isEmpty()
                                && downloads.snapshot().usedStorageBytes() == 0L,
                        "per-title download removal must clear every job and managed page");
                DownloadId replacement = downloads.enqueue(item.id(), source.unit("a").id());
                await(downloads, replacement, job -> job.status() == DownloadStatus.COMPLETED);
                downloads.removeAll();
                counter.check(downloads.snapshot().jobs().isEmpty()
                                && downloads.snapshot().usedStorageBytes() == 0L,
                        "delete-all must clear queue metadata and downloaded files");
            }
            try (DefaultDownloadService restarted = new DefaultDownloadService(
                    new SingleSourceRegistry(source), library, queueRoot, policy)) {
                counter.check(restarted.storage().location().equals(customStorage.toAbsolutePath().normalize()),
                        "selected download storage must survive a service restart");
            }

            recoveryRoot = Files.createTempDirectory("anilib-download-recovery");
            QueuedPagedSource flaky = new QueuedPagedSource(true, false);
            try (DefaultDownloadService downloads = new DefaultDownloadService(
                    new SingleSourceRegistry(flaky), library, recoveryRoot, policy)) {
                DownloadId id = downloads.enqueue(item.id(), flaky.unit("b").id());
                DownloadJobSnapshot failed = await(
                        downloads,
                        id,
                        job -> job.status() == DownloadStatus.FAILED);
                counter.check(failed.hasPartialData() && failed.failedPageIndex().orElseThrow() == 1,
                        "failed downloads must identify resumable partial data and the failed page");
                downloads.retry(id, DownloadRecoveryMode.RESTART);
                await(downloads, id, job -> job.status() == DownloadStatus.COMPLETED);
                counter.check(Collections.frequency(flaky.readUnits(), "b:0") == 2,
                        "restart recovery must discard and fetch partial pages again");
            }
        } catch (IOException exception) {
            throw new AssertionError("Unable to prepare priority and recovery download test", exception);
        } finally {
            deleteTree(queueRoot);
            deleteTree(recoveryRoot);
            deleteTree(customStorage);
        }
    }

    private static void verifiesStandardOfflineReading(Counter counter) {
        Path dataDirectory = null;
        LibraryItemId itemId;
        DownloadId downloadId;
        try {
            dataDirectory = Files.createTempDirectory("anilib-download-offline");
            Path publication = Files.createDirectories(
                    dataDirectory.resolve("local-content").resolve("Offline Book"));
            Files.write(publication.resolve("001.png"), FIRST_PAGE);
            Files.write(publication.resolve("002.png"), SECOND_PAGE);
            try (StartedAnilib product = StandardAnilib.start(dataDirectory)) {
                LibraryCatalog library = product.capability(LibraryCapabilities.CATALOG);
                LibraryItem item = LibraryItem.create("Offline Book", MediaKind.MANGA)
                        .withOrigin(new LibraryOrigin("anilib.local", "DIRECTORY:Offline Book"));
                itemId = item.id();
                library.save(item);
                DownloadService downloads = product.capability(DownloadCapabilities.SERVICE);
                SourceContentUnitId requestedUnit = product.capability(ReaderCapabilities.SERVICE)
                        .contentUnits(item.id())
                        .getFirst()
                        .id();
                downloadId = downloads.enqueue(item.id(), requestedUnit);
                DownloadJobSnapshot completed = await(
                        downloads,
                        downloadId,
                        job -> job.status() == DownloadStatus.COMPLETED);
                counter.check(completed.completedPages() == 2,
                        "download queue must persist every source page");
                counter.check(completed.contentUnit().id().equals(requestedUnit),
                        "download queue must target the exact chapter selected by Reader");
                counter.check(downloads.snapshot().usedStorageBytes() == 7,
                        "download storage usage must equal persisted page bytes");
                downloads.setOfflineMode(true);
                deleteTree(publication);
                ReaderService reader = product.capability(ReaderCapabilities.SERVICE);
                counter.check(reader.canOpen(item.id()),
                        "Reader must recognize completed downloads in offline mode");
                try (ReaderSession session = reader.open(item.id())) {
                    counter.check(Arrays.equals(session.currentPage(), FIRST_PAGE),
                            "Reader must load its page from Download storage while offline");
                }
            }

            try (StartedAnilib product = StandardAnilib.start(dataDirectory)) {
                DownloadService downloads = product.capability(DownloadCapabilities.SERVICE);
                counter.check(downloads.snapshot().offlineMode(),
                        "offline mode must survive a product restart");
                counter.check(downloads.snapshot().jobs().getFirst().status() == DownloadStatus.COMPLETED,
                        "completed download metadata must survive a product restart");
                try (ReaderSession session = product.capability(ReaderCapabilities.SERVICE).open(itemId)) {
                    counter.check(Arrays.equals(session.currentPage(), FIRST_PAGE),
                            "downloaded pages must remain readable after restart");
                }
                downloads.remove(downloadId);
                counter.check(downloads.snapshot().jobs().isEmpty(),
                        "removing a download must remove its durable queue entry");
                counter.check(downloads.snapshot().usedStorageBytes() == 0,
                        "removing a download must reclaim its accounted storage");
                counter.check(!product.capability(ReaderCapabilities.SERVICE).canOpen(itemId),
                        "offline Reader must reject titles after their download is removed");
            }
        } catch (IOException exception) {
            throw new AssertionError("Unable to prepare offline download test", exception);
        } finally {
            deleteTree(dataDirectory);
        }
    }

    private static void verifiesResumableQueue(Counter counter) {
        Path root = null;
        DefaultDownloadService first = null;
        DefaultDownloadService resumed = null;
        try {
            root = Files.createTempDirectory("anilib-download-resume");
            MemoryLibraryCatalog library = new MemoryLibraryCatalog();
            LibraryItem item = LibraryItem.create("Resume", MediaKind.MANGA)
                    .withOrigin(new LibraryOrigin("test.download", "title"));
            library.save(item);
            BlockingPagedSource source = new BlockingPagedSource(true);
            SourceRegistry registry = new SingleSourceRegistry(source);
            DownloadStoragePolicy policy = new DownloadStoragePolicy(1024, 128, 1, true, true);
            first = new DefaultDownloadService(registry, library, root, policy);
            DownloadId id = first.enqueue(item.id());
            await(first, id, job -> job.completedPages() == 1);
            counter.check(source.awaitSecondPage(),
                    "resumable test source must observe the in-flight second page");
            first.close();
            first = null;
            source.awaitReleased();

            resumed = new DefaultDownloadService(registry, library, root, policy);
            DownloadJobSnapshot completed = await(
                    resumed,
                    id,
                    job -> job.status() == DownloadStatus.COMPLETED);
            counter.check(completed.completedPages() == 2,
                    "queue startup must resume an interrupted page set");
            counter.check(Collections.frequency(source.readIndexes(), 0) == 1,
                    "resume must not fetch an already persisted page again");
            counter.check(Collections.frequency(source.readIndexes(), 1) >= 2,
                    "resume must retry the interrupted page");
            resumed.remove(id);
            counter.check(resumed.snapshot().usedStorageBytes() == 0,
                    "queue removal must reclaim resumed page files");
        } catch (IOException exception) {
            throw new AssertionError("Unable to prepare resumable download test", exception);
        } finally {
            if (first != null) {
                first.close();
            }
            if (resumed != null) {
                resumed.close();
            }
            deleteTree(root);
        }
    }

    private static void verifiesStorageLimit(Counter counter) {
        Path root = null;
        try {
            root = Files.createTempDirectory("anilib-download-limit");
            MemoryLibraryCatalog library = new MemoryLibraryCatalog();
            LibraryItem item = LibraryItem.create("Limit", MediaKind.MANGA)
                    .withOrigin(new LibraryOrigin("test.download", "title"));
            library.save(item);
            try (DefaultDownloadService downloads = new DefaultDownloadService(
                    new SingleSourceRegistry(new BlockingPagedSource(false)),
                    library,
                    root,
                    new DownloadStoragePolicy(6, 6, 1, true, true))) {
                counter.expectDownloadFailure(
                        () -> downloads.enqueue(item.id()),
                        "known page sizes exceeding storage policy must be rejected before queueing");
            }
        } catch (IOException exception) {
            throw new AssertionError("Unable to prepare download limit test", exception);
        } finally {
            deleteTree(root);
        }
    }

    private static void enforcesLargeTransferPolicy(Counter counter) {
        Path root = null;
        try {
            root = Files.createTempDirectory("anilib-download-network-policy");
            MemoryLibraryCatalog library = new MemoryLibraryCatalog();
            LibraryItem item = LibraryItem.create("Wi-Fi policy", MediaKind.MANGA)
                    .withOrigin(new LibraryOrigin("test.download", "title"));
            library.save(item);
            try (DefaultDownloadService downloads = new DefaultDownloadService(
                    new SingleSourceRegistry(new BlockingPagedSource(false)),
                    library,
                    root,
                    DownloadStoragePolicy.standard(),
                    () -> false)) {
                counter.check(!downloads.canEnqueue(item.id()),
                        "download eligibility must honor the current large-transfer network policy");
                counter.expectDownloadFailure(
                        () -> downloads.enqueue(item.id()),
                        "download enqueue must reject a disallowed network connection");
            }
        } catch (IOException exception) {
            throw new AssertionError("Unable to prepare download network policy test", exception);
        } finally {
            deleteTree(root);
        }
    }

    private static void cleansOrphanedDownloads(Counter counter) {
        Path root = null;
        try {
            root = Files.createTempDirectory("anilib-download-cleanup");
            MemoryLibraryCatalog library = new MemoryLibraryCatalog();
            LibraryItem item = LibraryItem.create("Cleanup", MediaKind.MANGA)
                    .withOrigin(new LibraryOrigin("test.download", "title"));
            library.save(item);
            try (DefaultDownloadService downloads = new DefaultDownloadService(
                    new SingleSourceRegistry(new BlockingPagedSource(false)),
                    library,
                    root,
                    DownloadStoragePolicy.standard())) {
                DownloadId id = downloads.enqueue(item.id());
                await(downloads, id, job -> job.status() == DownloadStatus.COMPLETED);
                library.remove(item.id());
                counter.check(downloads.cleanUnusedData() == 1 && downloads.snapshot().jobs().isEmpty(),
                        "database cleanup must remove download records for deleted library titles");
                counter.check(downloads.snapshot().usedStorageBytes() == 0,
                        "database cleanup must reclaim orphaned download page files");
            }
        } catch (IOException exception) {
            throw new AssertionError("Unable to prepare download cleanup test", exception);
        } finally {
            deleteTree(root);
        }
    }

    private static DownloadJobSnapshot await(
            DownloadService downloads,
            DownloadId id,
            Predicate<DownloadJobSnapshot> predicate) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        DownloadJobSnapshot latest = null;
        while (System.nanoTime() < deadline) {
            latest = downloads.snapshot().jobs().stream()
                    .filter(job -> job.id().equals(id))
                    .findFirst()
                    .orElseThrow();
            if (predicate.test(latest)) {
                return latest;
            }
            Thread.onSpinWait();
        }
        throw new AssertionError("Download job did not reach expected state: " + latest);
    }

    private static void deleteTree(Path root) {
        if (root == null || !Files.exists(root)) {
            return;
        }
        try (java.util.stream.Stream<Path> paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        } catch (IOException exception) {
            throw new AssertionError("Unable to clean download test directory", exception);
        }
    }

    private static final class BlockingPagedSource implements PagedSource {
        private final SourceCatalogueItemId itemId = new SourceCatalogueItemId(
                SourceId.of("test.download"),
                "title");
        private final SourceContentUnit unit = new SourceContentUnit(
                new SourceContentUnitId(itemId, "chapter"),
                "Chapter",
                Optional.of(Instant.EPOCH));
        private final CountDownLatch secondPage = new CountDownLatch(1);
        private final List<Integer> readIndexes = Collections.synchronizedList(new ArrayList<>());
        private volatile boolean blockSecond;

        private BlockingPagedSource(boolean blockSecond) {
            this.blockSecond = blockSecond;
        }

        @Override
        public SourceDescriptor descriptor() {
            return new SourceDescriptor(
                    itemId.sourceId(),
                    "Download test",
                    "1.0.0",
                    "und",
                    Set.of(SourceContentKind.MANGA),
                    SourceSdk.API_VERSION);
        }

        @Override
        public List<SourceContentUnit> contentUnits(SourceCatalogueItemId requested) {
            return requested.equals(itemId) ? List.of(unit) : List.of();
        }

        @Override
        public List<SourcePageResource> pages(SourceContentUnitId requested) {
            return List.of(
                    new SourcePageResource(requested, "first", 0, FIRST_PAGE.length),
                    new SourcePageResource(requested, "second", 1, SECOND_PAGE.length));
        }

        @Override
        public byte[] readPage(SourcePageResource page) {
            readIndexes.add(page.index());
            if (page.index() == 1 && blockSecond) {
                secondPage.countDown();
                try {
                    new CountDownLatch(1).await();
                } catch (InterruptedException exception) {
                    blockSecond = false;
                    Thread.currentThread().interrupt();
                    throw new DownloadException("Interrupted test page", exception);
                }
            }
            return page.index() == 0 ? FIRST_PAGE.clone() : SECOND_PAGE.clone();
        }

        private boolean awaitSecondPage() {
            try {
                return secondPage.await(3, TimeUnit.SECONDS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new AssertionError("Interrupted while awaiting test download", exception);
            }
        }

        private void awaitReleased() {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
            while (blockSecond && System.nanoTime() < deadline) {
                Thread.onSpinWait();
            }
            if (blockSecond) {
                throw new AssertionError("Interrupted test page did not release");
            }
        }

        private List<Integer> readIndexes() {
            return List.copyOf(readIndexes);
        }
    }

    private static final class QueuedPagedSource implements PagedSource {
        private final SourceCatalogueItemId itemId = new SourceCatalogueItemId(
                SourceId.of("test.queue"),
                "title");
        private final List<SourceContentUnit> units = List.of(
                contentUnit("a"),
                contentUnit("b"),
                contentUnit("c"));
        private final boolean failBOnce;
        private final boolean blockA;
        private final AtomicBoolean failed = new AtomicBoolean();
        private final CountDownLatch blocked = new CountDownLatch(1);
        private final CountDownLatch released = new CountDownLatch(1);
        private final List<String> reads = Collections.synchronizedList(new ArrayList<>());
        private final List<String> completed = Collections.synchronizedList(new ArrayList<>());

        private QueuedPagedSource(boolean failBOnce, boolean blockA) {
            this.failBOnce = failBOnce;
            this.blockA = blockA;
        }

        @Override
        public SourceDescriptor descriptor() {
            return new SourceDescriptor(
                    itemId.sourceId(),
                    "Queue test",
                    "1.0.0",
                    "und",
                    Set.of(SourceContentKind.MANGA),
                    SourceSdk.API_VERSION);
        }

        @Override
        public List<SourceContentUnit> contentUnits(SourceCatalogueItemId requested) {
            return requested.equals(itemId) ? units : List.of();
        }

        @Override
        public List<SourcePageResource> pages(SourceContentUnitId requested) {
            return List.of(
                    new SourcePageResource(requested, requested.value() + "-0", 0, 4L),
                    new SourcePageResource(requested, requested.value() + "-1", 1, 4L));
        }

        @Override
        public byte[] readPage(SourcePageResource page) {
            String unit = page.contentUnitId().value();
            reads.add(unit + ":" + page.index());
            if (unit.equals("a") && page.index() == 1 && blockA) {
                blocked.countDown();
                try {
                    if (!released.await(3, TimeUnit.SECONDS)) {
                        throw new DownloadException("Timed out waiting to release priority test");
                    }
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new DownloadException("Interrupted priority test", exception);
                }
            }
            if (unit.equals("b") && page.index() == 1 && failBOnce && failed.compareAndSet(false, true)) {
                throw new DownloadException("Synthetic second-page failure");
            }
            if (page.index() == 1) {
                completed.add(unit);
            }
            return new byte[] {1, 2, 3, 4};
        }

        private SourceContentUnit unit(String id) {
            return units.stream()
                    .filter(unit -> unit.id().value().equals(id))
                    .findFirst()
                    .orElseThrow();
        }

        private boolean awaitBlocked() {
            try {
                return blocked.await(3, TimeUnit.SECONDS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new AssertionError("Interrupted while awaiting priority download", exception);
            }
        }

        private void release() {
            released.countDown();
        }

        private List<String> completionOrder() {
            return List.copyOf(completed);
        }

        private List<String> readUnits() {
            return List.copyOf(reads);
        }

        private SourceContentUnit contentUnit(String id) {
            return new SourceContentUnit(
                    new SourceContentUnitId(itemId, id),
                    "Chapter " + id,
                    Optional.of(Instant.EPOCH));
        }
    }

    private record SingleSourceRegistry(Source source) implements SourceRegistry {
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

        private void expectDownloadFailure(Runnable action, String message) {
            try {
                action.run();
                throw new AssertionError(message);
            } catch (DownloadException expected) {
                value++;
            }
        }
    }
}
