package fr.vriege.anilib.tooling.archtests;

import fr.vriege.anilib.configuration.standard.StandardAnilib;
import fr.vriege.anilib.feature.downloads.DownloadCapabilities;
import fr.vriege.anilib.feature.downloads.DownloadContentType;
import fr.vriege.anilib.feature.downloads.AutomaticDownloadCategoryRule;
import fr.vriege.anilib.feature.downloads.AutomaticDownloadPolicy;
import fr.vriege.anilib.feature.downloads.DownloadCleanupPolicy;
import fr.vriege.anilib.feature.downloads.DownloadException;
import fr.vriege.anilib.feature.downloads.DownloadId;
import fr.vriege.anilib.feature.downloads.DownloadJobSnapshot;
import fr.vriege.anilib.feature.downloads.DownloadIndexRepairResult;
import fr.vriege.anilib.feature.downloads.DownloadPriority;
import fr.vriege.anilib.feature.downloads.DownloadQueueSnapshot;
import fr.vriege.anilib.feature.downloads.DownloadRecoveryMode;
import fr.vriege.anilib.feature.downloads.DownloadService;
import fr.vriege.anilib.feature.downloads.DownloadStatus;
import fr.vriege.anilib.feature.downloads.DownloadStoragePolicy;
import fr.vriege.anilib.feature.downloads.VideoDownloadFinalizer;
import fr.vriege.anilib.feature.downloads.runtime.DefaultDownloadService;
import fr.vriege.anilib.feature.library.LibraryCapabilities;
import fr.vriege.anilib.feature.library.LibraryCatalog;
import fr.vriege.anilib.feature.library.LibraryItem;
import fr.vriege.anilib.feature.library.LibraryItemId;
import fr.vriege.anilib.feature.library.LibraryOrigin;
import fr.vriege.anilib.feature.library.MediaKind;
import fr.vriege.anilib.feature.player.PlayerBackend;
import fr.vriege.anilib.feature.player.PlayerMedia;
import fr.vriege.anilib.feature.player.PlayerPlayback;
import fr.vriege.anilib.feature.player.PlayerPlaybackSnapshot;
import fr.vriege.anilib.feature.player.PlayerPlaybackStatus;
import fr.vriege.anilib.feature.player.PlayerSession;
import fr.vriege.anilib.feature.player.runtime.DefaultPlayerService;
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
import fr.vriege.anilib.feature.source.SourceEpisode;
import fr.vriege.anilib.feature.source.SourceEpisodeId;
import fr.vriege.anilib.feature.source.SourceStreamFormat;
import fr.vriege.anilib.feature.source.SourceVideoStream;
import fr.vriege.anilib.feature.source.StreamingSource;
import fr.vriege.anilib.framework.http.AnilibHttpClient;
import fr.vriege.anilib.framework.http.HttpResponse;
import fr.vriege.anilib.kernel.StartedAnilib;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import java.util.Collection;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
        verifiesConfigurableConcurrentQueue(counter);
        verifiesAutomaticRules(counter);
        verifiesStorageLimit(counter);
        downloadsHlsEpisodes(counter);
        keepsQueueResponsiveDuringVideoPreparation(counter);
        finalizesHlsEpisodesAsSingleMedia(counter);
        removesVideosSafelyDuringFinalization(counter);
        enforcesLargeTransferPolicy(counter);
        cleansOrphanedDownloads(counter);
        return counter.value;
    }

    private static void downloadsHlsEpisodes(Counter counter) {
        Path root = null;
        try {
            root = Files.createTempDirectory("anilib-anime-download");
            MemoryLibraryCatalog library = new MemoryLibraryCatalog();
            LibraryItem item = LibraryItem.create("Anime", MediaKind.ANIME)
                    .withOrigin(new LibraryOrigin("test.streaming", "title"));
            library.save(item);
            HlsStreamingSource source = new HlsStreamingSource();
            byte[] first = {1, 2, 3};
            byte[] second = {4, 5, 6, 7};
            AnilibHttpClient client = request -> {
                String location = request.uri().toString();
                if (location.endsWith("playlist.m3u8")) {
                    String playlist = "#EXTM3U\n#EXT-X-TARGETDURATION:10\n"
                            + "#EXTINF:10,\nfirst.ts\n#EXTINF:10,\nsecond.ts\n#EXT-X-ENDLIST\n";
                    return new HttpResponse(200, Map.of(), playlist.getBytes(StandardCharsets.UTF_8), false);
                }
                if (location.endsWith("first.ts")) {
                    return new HttpResponse(200, Map.of(), first, false);
                }
                if (location.endsWith("second.ts")) {
                    return new HttpResponse(200, Map.of(), second, false);
                }
                return new HttpResponse(404, Map.of(), new byte[0], false);
            };
            DownloadStoragePolicy policy = new DownloadStoragePolicy(4096, 1024, 1, true, true);
            try (DefaultDownloadService downloads = new DefaultDownloadService(
                    new SingleSourceRegistry(source), library, root, policy, () -> true, client)) {
                counter.check(downloads.canEnqueue(item.id()),
                        "streaming titles must expose episode downloads");
                DownloadId id = downloads.enqueue(item.id(), source.episode.id().value());
                DownloadJobSnapshot completed = await(
                        downloads,
                        id,
                        job -> job.status() == DownloadStatus.COMPLETED);
                counter.check(completed.completedPages() == 2
                                && completed.downloadedBytes() == first.length + second.length
                                && completed.contentType() == DownloadContentType.VIDEO,
                        "HLS downloads must persist every media segment");
                Path directory = root.resolve("content").resolve(id.toString());
                counter.check(Files.isRegularFile(directory.resolve("offline.m3u8"))
                                && Files.isRegularFile(directory.resolve("00000000.page"))
                                && Files.isRegularFile(directory.resolve("00000001.page")),
                        "HLS downloads must create a self-contained offline playlist");
                counter.check(downloads.episodes(source.itemId).equals(List.of(source.episode))
                                && downloads.streams(source.episode.id()).stream()
                                        .map(SourceVideoStream::location)
                                        .allMatch(location -> location.getScheme().equals("file")),
                        "completed anime downloads must become local Player content");
            }
        } catch (IOException exception) {
            throw new AssertionError("Unable to prepare anime download test", exception);
        } finally {
            deleteTree(root);
        }
    }

    private static void keepsQueueResponsiveDuringVideoPreparation(Counter counter) {
        Path root = null;
        HlsStreamingSource source = new HlsStreamingSource(true);
        try {
            root = Files.createTempDirectory("anilib-anime-preparation");
            MemoryLibraryCatalog library = new MemoryLibraryCatalog();
            LibraryItem item = LibraryItem.create("Preparing Anime", MediaKind.ANIME)
                    .withOrigin(new LibraryOrigin("test.streaming", "title"));
            library.save(item);
            AnilibHttpClient client = request -> new HttpResponse(
                    200,
                    Map.of(),
                    "#EXTM3U\n#EXTINF:10,\nsegment.ts\n#EXT-X-ENDLIST\n".getBytes(StandardCharsets.UTF_8),
                    false);
            DownloadStoragePolicy policy = new DownloadStoragePolicy(4096, 1024, 1, true, true);
            try (DefaultDownloadService downloads = new DefaultDownloadService(
                    new SingleSourceRegistry(source), library, root, policy, () -> true, client)) {
                CompletableFuture<DownloadId> preparation = CompletableFuture.supplyAsync(
                        () -> downloads.enqueue(item.id(), source.episode.id().value()));
                counter.check(source.awaitStreamResolution(),
                        "video preparation must reach the deliberately slow extension call");
                long started = System.nanoTime();
                boolean readable = downloads.snapshot().jobs().isEmpty();
                long elapsed = System.nanoTime() - started;
                counter.check(readable && elapsed < TimeUnit.MILLISECONDS.toNanos(250L),
                        "slow video preparation must not lock queue snapshots");
                source.releaseStreamResolution();
                DownloadId id = preparation.orTimeout(5L, TimeUnit.SECONDS).join();
                await(downloads, id, job -> job.status() == DownloadStatus.COMPLETED);
            }
        } catch (IOException exception) {
            throw new AssertionError("Unable to prepare responsive queue test", exception);
        } finally {
            source.releaseStreamResolution();
            deleteTree(root);
        }
    }

    private static void finalizesHlsEpisodesAsSingleMedia(Counter counter) {
        Path root = null;
        try {
            root = Files.createTempDirectory("anilib-anime-finalization");
            MemoryLibraryCatalog library = new MemoryLibraryCatalog();
            LibraryItem item = LibraryItem.create("Finalized Anime", MediaKind.ANIME)
                    .withOrigin(new LibraryOrigin("test.streaming", "title"));
            library.save(item);
            HlsStreamingSource source = new HlsStreamingSource();
            AnilibHttpClient client = request -> {
                String location = request.uri().toString();
                if (location.endsWith("playlist.m3u8")) {
                    String playlist = "#EXTM3U\n#EXT-X-TARGETDURATION:10\n"
                            + "#EXTINF:10,\nfirst.ts\n#EXTINF:10,\nsecond.ts\n#EXT-X-ENDLIST\n";
                    return new HttpResponse(200, Map.of(), playlist.getBytes(StandardCharsets.UTF_8), false);
                }
                return new HttpResponse(200, Map.of(), new byte[] {1, 2, 3}, false);
            };
            AtomicBoolean invoked = new AtomicBoolean();
            VideoDownloadFinalizer finalizer = new VideoDownloadFinalizer() {
                @Override
                public boolean available() {
                    return true;
                }

                @Override
                public void finalizeVideo(VideoFinalizationRequest request, BooleanSupplier cancelled) {
                    counter.check(request.input().getFileName().toString().equals("offline.m3u8")
                                    && request.output().getFileName().toString().equals("offline.mp4")
                                    && !cancelled.getAsBoolean(),
                            "video finalization must receive local managed sibling paths");
                    try {
                        Files.write(request.output(), new byte[] {9, 8, 7, 6});
                        invoked.set(true);
                    } catch (IOException exception) {
                        throw new DownloadException("Unable to emulate video finalization", exception);
                    }
                }
            };
            DownloadStoragePolicy policy = new DownloadStoragePolicy(4096, 1024, 1, true, true);
            try (DefaultDownloadService downloads = new DefaultDownloadService(
                    new SingleSourceRegistry(source),
                    library,
                    root,
                    policy,
                    () -> true,
                    client,
                    finalizer)) {
                DownloadId id = downloads.enqueue(item.id(), source.episode.id().value());
                DownloadJobSnapshot completed = await(
                        downloads,
                        id,
                        job -> job.status() == DownloadStatus.COMPLETED);
                Path directory = root.resolve("content").resolve(id.toString());
                counter.check(invoked.get()
                                && completed.downloadedBytes() == 4L
                                && downloads.snapshot().usedStorageBytes() == 4L,
                        "completed finalized videos must account only for their final media file");
                counter.check(Files.isRegularFile(directory.resolve("offline.mp4"))
                                && !Files.exists(directory.resolve("offline.m3u8"))
                                && !Files.exists(directory.resolve("00000000.page"))
                                && !Files.exists(directory.resolve("00000001.page")),
                        "video fragments must be removed only after finalization succeeds");
                SourceVideoStream offline = downloads.streams(source.episode.id()).getFirst();
                counter.check(offline.location().getPath().endsWith("offline.mp4")
                                && offline.format() == SourceStreamFormat.PROGRESSIVE,
                        "the offline player must receive the finalized MKV instead of HLS fragments");
                try (DefaultPlayerService player = new DefaultPlayerService(
                        new SingleSourceRegistry(source),
                        library,
                        root.resolve("playback-state.anilib"),
                        new TestPlayerBackend())) {
                    player.register(downloads);
                    int streamRequestsBeforePlayer = source.streamRequests.get();
                    try (PlayerSession session = player.open(item.id(), source.episode.id())) {
                        counter.check(session.snapshot().streams().size() == 1
                                        && session.snapshot().streams().stream()
                                                .allMatch(stream -> "file".equals(stream.location().getScheme()))
                                        && source.streamRequests.get() == streamRequestsBeforePlayer
                                        && session.onlineStreamsAvailable(),
                                "downloaded anime must open locally without resolving its online streams");
                        downloads.setOfflineMode(true);
                        counter.check(!session.onlineStreamsAvailable(),
                                "offline mode must hide the online playback choice from an open session");
                        downloads.setOfflineMode(false);
                        counter.check(session.onlineStreamsAvailable(),
                                "leaving offline mode must restore the online playback choice");
                        session.loadOnlineStreams();
                        counter.check(session.snapshot().streams().size() == 2
                                        && source.streamRequests.get() == streamRequestsBeforePlayer + 1,
                                "online streams must resolve only when the user requests them");
                        session.selectStream("hls");
                        counter.check("https".equals(session.playback().media().stream().location().getScheme()),
                                "the player must be able to switch from its local file to an online stream");
                    }
                }
            }
        } catch (IOException exception) {
            throw new AssertionError("Unable to prepare finalized anime download test", exception);
        } finally {
            deleteTree(root);
        }
    }

    private static void removesVideosSafelyDuringFinalization(Counter counter) {
        Path root = null;
        try {
            root = Files.createTempDirectory("anilib-anime-finalization-removal");
            MemoryLibraryCatalog library = new MemoryLibraryCatalog();
            LibraryItem item = LibraryItem.create("Removed Anime", MediaKind.ANIME)
                    .withOrigin(new LibraryOrigin("test.streaming", "title"));
            library.save(item);
            HlsStreamingSource source = new HlsStreamingSource();
            AnilibHttpClient client = request -> {
                if (request.uri().getPath().endsWith("playlist.m3u8")) {
                    String playlist = "#EXTM3U\n#EXTINF:10,\nsegment-one.ts\n#EXT-X-ENDLIST\n";
                    return new HttpResponse(200, Map.of(), playlist.getBytes(StandardCharsets.UTF_8), false);
                }
                return new HttpResponse(200, Map.of(), new byte[] {1, 2, 3}, false);
            };
            CountDownLatch finalizationStarted = new CountDownLatch(1);
            CountDownLatch finalizationStopped = new CountDownLatch(1);
            VideoDownloadFinalizer finalizer = new VideoDownloadFinalizer() {
                @Override
                public boolean available() {
                    return true;
                }

                @Override
                public void finalizeVideo(VideoFinalizationRequest request, BooleanSupplier cancelled) {
                    finalizationStarted.countDown();
                    try {
                        while (!cancelled.getAsBoolean()) {
                            TimeUnit.MILLISECONDS.sleep(10L);
                        }
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                        throw new DownloadException("Interrupted finalization test", exception);
                    } finally {
                        finalizationStopped.countDown();
                    }
                }
            };
            DownloadStoragePolicy policy = new DownloadStoragePolicy(4096, 1024, 1, true, true);
            try (DefaultDownloadService downloads = new DefaultDownloadService(
                    new SingleSourceRegistry(source),
                    library,
                    root,
                    policy,
                    () -> true,
                    client,
                    finalizer)) {
                DownloadId id = downloads.enqueue(item.id(), source.episode.id().value());
                counter.check(finalizationStarted.await(5L, TimeUnit.SECONDS),
                        "video finalization must start before removal is exercised");
                downloads.remove(id);
                counter.check(downloads.snapshot().jobs().stream().noneMatch(job -> job.id().equals(id)),
                        "removing a finalizing video must hide it from the queue immediately");
                counter.check(finalizationStopped.await(5L, TimeUnit.SECONDS),
                        "removing a finalizing video must cancel its finalizer");
                Path directory = root.resolve("content").resolve(id.toString());
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5L);
                while (Files.exists(directory) && System.nanoTime() < deadline) {
                    TimeUnit.MILLISECONDS.sleep(10L);
                }
                counter.check(!Files.exists(directory),
                        "video files must be deleted after the finalizer releases them");
            }
        } catch (IOException exception) {
            throw new AssertionError("Unable to prepare video removal test", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while testing video removal", exception);
        } finally {
            deleteTree(root);
        }
    }

    private static void verifiesAutomaticRules(Counter counter) {
        Path root = null;
        try {
            root = Files.createTempDirectory("anilib-automatic-downloads");
            MemoryLibraryCatalog library = new MemoryLibraryCatalog();
            LibraryItem item = LibraryItem.create("Automatic", MediaKind.MANGA)
                    .withCategories(Set.of("Weekly"))
                    .withOrigin(new LibraryOrigin("test.queue", "title"));
            library.save(item);
            QueuedPagedSource source = new QueuedPagedSource(false, false);
            DownloadStoragePolicy storage = new DownloadStoragePolicy(4096, 1024, 1, true, true);
            AutomaticDownloadPolicy automatic = new AutomaticDownloadPolicy(
                    true,
                    false,
                    false,
                    0,
                    1,
                    DownloadCleanupPolicy.KEEP_LATEST,
                    1,
                    List.of(new AutomaticDownloadCategoryRule("Weekly", 0, 2)));
            try (DefaultDownloadService downloads = new DefaultDownloadService(
                    new SingleSourceRegistry(source), library, root, storage)) {
                downloads.configureAutomaticDownloads(automatic);
                List<DownloadJobSnapshot> jobs = downloads.snapshot().jobs();
                counter.check(jobs.size() == 2
                                && jobs.stream().map(job -> job.contentUnit().id().value()).collect(
                                        Collectors.toSet()).equals(Set.of("b", "c")),
                        "automatic category rules must queue only the configured latest chapter limit");
                for (DownloadJobSnapshot job : jobs) {
                    await(downloads, job.id(), value -> value.status() == DownloadStatus.COMPLETED);
                }
                counter.check(downloads.cleanAutomaticDownloads() == 1
                                && downloads.snapshot().jobs().size() == 1,
                        "automatic cleanup must retain only the configured latest completed item");
            }
            try (DefaultDownloadService restarted = new DefaultDownloadService(
                    new SingleSourceRegistry(source), library, root, storage)) {
                counter.check(restarted.automaticPolicy().equals(automatic),
                        "automatic download rules must survive a service restart");
            }
        } catch (IOException exception) {
            throw new AssertionError("Unable to prepare automatic download test", exception);
        } finally {
            deleteTree(root);
        }
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
                DownloadId id = downloads.enqueue(item.id(), "b");
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
            SourceRegistry registry = new SingleSourceRegistry(new BlockingPagedSource(false));
            DownloadStoragePolicy initial = new DownloadStoragePolicy(6, 6, 1, true, true);
            try (DefaultDownloadService downloads = new DefaultDownloadService(
                    registry, library, root, initial)) {
                counter.expectDownloadFailure(
                        () -> downloads.enqueue(item.id()),
                        "known page sizes exceeding storage policy must be rejected before queueing");
                downloads.configureMaximumStorageBytes(12L);
                counter.check(downloads.snapshot().maximumStorageBytes() == 12L,
                        "download storage limit changes must apply immediately");
                DownloadId id = downloads.enqueue(item.id());
                counter.check(await(downloads, id, job -> job.status() == DownloadStatus.COMPLETED)
                                .contentType() == DownloadContentType.PAGES,
                        "manga downloads must remain page content");
            }
            try (DefaultDownloadService restarted = new DefaultDownloadService(
                    registry, library, root, initial)) {
                counter.check(restarted.snapshot().maximumStorageBytes() == 12L,
                        "configured download storage limits must survive a restart");
            }
        } catch (IOException exception) {
            throw new AssertionError("Unable to prepare download limit test", exception);
        } finally {
            deleteTree(root);
        }
    }

    private static void verifiesConfigurableConcurrentQueue(Counter counter) {
        Path root = null;
        ConcurrentPagedSource source = new ConcurrentPagedSource();
        try {
            root = Files.createTempDirectory("anilib-download-concurrency");
            MemoryLibraryCatalog library = new MemoryLibraryCatalog();
            LibraryItem item = LibraryItem.create("Concurrent queue", MediaKind.MANGA)
                    .withOrigin(new LibraryOrigin("test.concurrent", "title"));
            library.save(item);
            DownloadStoragePolicy initial = new DownloadStoragePolicy(4096, 1024, 1, true, true);
            try (DefaultDownloadService downloads = new DefaultDownloadService(
                    new SingleSourceRegistry(source), library, root, initial)) {
                downloads.configureConcurrentJobs(2);
                List<DownloadId> ids = source.units.stream()
                        .map(unit -> downloads.enqueue(item.id(), unit.id()))
                        .toList();
                counter.check(source.awaitStarted(2),
                        "the configured number of downloads must start simultaneously");
                DownloadQueueSnapshot snapshot = downloads.snapshot();
                counter.check(snapshot.concurrentJobs() == 2
                                && snapshot.jobs().stream()
                                        .filter(job -> job.status() == DownloadStatus.DOWNLOADING)
                                        .count() == 2L
                                && snapshot.jobs().stream()
                                        .filter(job -> job.status() == DownloadStatus.QUEUED)
                                        .count() == 1L,
                        "jobs beyond the concurrency limit must remain in the application queue");
                downloads.configureConcurrentJobs(3);
                counter.check(source.awaitStarted(3) && source.maximumActive.get() == 3,
                        "raising the limit must immediately fill the newly available slot");
                source.release();
                ids.forEach(id -> await(downloads, id, job -> job.status() == DownloadStatus.COMPLETED));
            }
            try (DefaultDownloadService restarted = new DefaultDownloadService(
                    new SingleSourceRegistry(source), library, root, initial)) {
                counter.check(restarted.snapshot().concurrentJobs() == 3,
                        "the simultaneous download limit must survive a restart");
            }
        } catch (IOException exception) {
            throw new AssertionError("Unable to prepare concurrent queue test", exception);
        } finally {
            source.release();
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
        try (Stream<Path> paths = Files.walk(root)) {
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

    private static final class ConcurrentPagedSource implements PagedSource {
        private final SourceCatalogueItemId itemId = new SourceCatalogueItemId(
                SourceId.of("test.concurrent"),
                "title");
        private final List<SourceContentUnit> units = List.of(
                contentUnit("a"),
                contentUnit("b"),
                contentUnit("c"));
        private final CountDownLatch started = new CountDownLatch(3);
        private final CountDownLatch released = new CountDownLatch(1);
        private final AtomicInteger active = new AtomicInteger();
        private final AtomicInteger maximumActive = new AtomicInteger();

        @Override
        public SourceDescriptor descriptor() {
            return new SourceDescriptor(
                    itemId.sourceId(),
                    "Concurrent queue test",
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
            return List.of(new SourcePageResource(requested, requested.value(), 0, 4L));
        }

        @Override
        public byte[] readPage(SourcePageResource page) {
            int current = active.incrementAndGet();
            maximumActive.accumulateAndGet(current, Math::max);
            started.countDown();
            try {
                if (!released.await(3L, TimeUnit.SECONDS)) {
                    throw new DownloadException("Timed out waiting to release concurrent download test");
                }
                return new byte[] {1, 2, 3, 4};
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new DownloadException("Interrupted concurrent download test", exception);
            } finally {
                active.decrementAndGet();
            }
        }

        private SourceContentUnit contentUnit(String id) {
            return new SourceContentUnit(
                    new SourceContentUnitId(itemId, id),
                    id,
                    Optional.of(Instant.EPOCH));
        }

        private boolean awaitStarted(int expected) {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3L);
            while (started.getCount() > 3L - expected && System.nanoTime() < deadline) {
                Thread.onSpinWait();
            }
            return started.getCount() <= 3L - expected;
        }

        private void release() {
            released.countDown();
        }
    }

    private static final class HlsStreamingSource implements StreamingSource {
        private final SourceCatalogueItemId itemId = new SourceCatalogueItemId(
                SourceId.of("test.streaming"),
                "title");
        private final SourceEpisode episode = new SourceEpisode(
                new SourceEpisodeId(itemId, "episode-1"),
                "Episode 1",
                1.0D,
                Optional.of(Instant.EPOCH),
                Optional.empty());
        private final boolean blockStreamResolution;
        private final AtomicInteger streamRequests = new AtomicInteger();
        private final CountDownLatch streamResolutionStarted = new CountDownLatch(1);
        private final CountDownLatch streamResolutionReleased = new CountDownLatch(1);

        private HlsStreamingSource() {
            this(false);
        }

        private HlsStreamingSource(boolean blockStreamResolution) {
            this.blockStreamResolution = blockStreamResolution;
        }

        @Override
        public SourceDescriptor descriptor() {
            return new SourceDescriptor(
                    itemId.sourceId(),
                    "Streaming test",
                    "1.0.0",
                    "und",
                    Set.of(SourceContentKind.ANIME),
                    SourceSdk.API_VERSION);
        }

        @Override
        public List<SourceEpisode> episodes(SourceCatalogueItemId requested) {
            return requested.equals(itemId) ? List.of(episode) : List.of();
        }

        @Override
        public List<SourceVideoStream> streams(SourceEpisodeId requested) {
            streamRequests.incrementAndGet();
            if (blockStreamResolution) {
                streamResolutionStarted.countDown();
                try {
                    if (!streamResolutionReleased.await(3L, TimeUnit.SECONDS)) {
                        throw new DownloadException("Timed out waiting to release video preparation");
                    }
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new DownloadException("Interrupted video preparation test", exception);
                }
            }
            return requested.equals(episode.id())
                    ? List.of(new SourceVideoStream(
                            "hls",
                            "1080p",
                            URI.create("https://video.test/playlist.m3u8"),
                            SourceStreamFormat.HLS,
                            Map.of("Referer", "https://anime.test/title"),
                            List.of()))
                    : List.of();
        }

        private boolean awaitStreamResolution() {
            try {
                return streamResolutionStarted.await(3L, TimeUnit.SECONDS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new AssertionError("Interrupted while awaiting video preparation", exception);
            }
        }

        private void releaseStreamResolution() {
            streamResolutionReleased.countDown();
        }
    }

    private static final class TestPlayerBackend implements PlayerBackend {
        @Override
        public String id() {
            return "download-test";
        }

        @Override
        public boolean available() {
            return true;
        }

        @Override
        public PlayerPlayback open(PlayerMedia media) {
            return new TestPlayerPlayback(media);
        }
    }

    private static final class TestPlayerPlayback implements PlayerPlayback {
        private final PlayerMedia media;

        private TestPlayerPlayback(PlayerMedia media) {
            this.media = media;
        }

        @Override
        public PlayerMedia media() {
            return media;
        }

        @Override
        public PlayerPlaybackSnapshot snapshot() {
            return new PlayerPlaybackSnapshot(
                    PlayerPlaybackStatus.PAUSED,
                    media.startPositionMillis(),
                    -1L,
                    1.0F,
                    1.0F,
                    Optional.empty());
        }

        @Override
        public void play() {
        }

        @Override
        public void pause() {
        }

        @Override
        public void seekTo(long positionMillis) {
        }

        @Override
        public void setVolume(float volume) {
        }

        @Override
        public void setPlaybackSpeed(float speed) {
        }

        @Override
        public void selectSubtitle(Optional<String> subtitleId) {
        }

        @Override
        public void close() {
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
        public synchronized void replaceAll(Collection<LibraryItem> replacement) {
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
