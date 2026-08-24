package fr.vriege.anilib.feature.downloads.runtime;

import fr.vriege.anilib.framework.concurrent.runtime.ManagedExecutors;
import fr.vriege.anilib.feature.downloads.AutomaticDownloadCategoryRule;
import fr.vriege.anilib.feature.downloads.AutomaticDownloadPolicy;
import fr.vriege.anilib.feature.downloads.AutomaticDownloadResult;
import fr.vriege.anilib.feature.downloads.DownloadCleanupPolicy;
import fr.vriege.anilib.feature.downloads.DownloadException;
import fr.vriege.anilib.feature.downloads.DownloadId;
import fr.vriege.anilib.feature.downloads.DownloadIndexRepairResult;
import fr.vriege.anilib.feature.downloads.DownloadJobSnapshot;
import fr.vriege.anilib.feature.downloads.DownloadQueueSnapshot;
import fr.vriege.anilib.feature.downloads.DownloadPriority;
import fr.vriege.anilib.feature.downloads.DownloadRecoveryMode;
import fr.vriege.anilib.feature.downloads.DownloadService;
import fr.vriege.anilib.feature.downloads.DownloadStatus;
import fr.vriege.anilib.feature.downloads.DownloadStoragePolicy;
import fr.vriege.anilib.feature.downloads.DownloadStorageSnapshot;
import fr.vriege.anilib.feature.downloads.VideoDownloadFinalizer;
import fr.vriege.anilib.feature.downloads.VideoDownloadFinalizer.VideoFinalizationRequest;
import fr.vriege.anilib.feature.library.LibraryCatalog;
import fr.vriege.anilib.feature.library.LibraryItem;
import fr.vriege.anilib.feature.library.LibraryItemId;
import fr.vriege.anilib.feature.library.LibraryOrigin;
import fr.vriege.anilib.feature.library.MediaKind;
import fr.vriege.anilib.feature.reader.ReaderContent;
import fr.vriege.anilib.feature.reader.ReaderContentProvider;
import fr.vriege.anilib.feature.player.PlayerContentProvider;
import fr.vriege.anilib.feature.source.PagedSource;
import fr.vriege.anilib.feature.source.Source;
import fr.vriege.anilib.feature.source.SourceCatalogueItemId;
import fr.vriege.anilib.feature.source.SourceContentUnit;
import fr.vriege.anilib.feature.source.SourceContentUnitId;
import fr.vriege.anilib.feature.source.SourceId;
import fr.vriege.anilib.feature.source.SourcePageResource;
import fr.vriege.anilib.feature.source.SourceRegistry;
import fr.vriege.anilib.feature.source.SourceEpisode;
import fr.vriege.anilib.feature.source.SourceEpisodeId;
import fr.vriege.anilib.feature.source.SourceVideoStream;
import fr.vriege.anilib.feature.source.StreamingSource;
import fr.vriege.anilib.feature.source.SourceStreamFormat;
import fr.vriege.anilib.framework.http.AnilibHttpClient;
import fr.vriege.anilib.framework.http.HttpException;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.StandardOpenOption;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class DefaultDownloadService
        implements DownloadService, ReaderContentProvider, PlayerContentProvider, AutoCloseable {
    private static final long PROGRESS_NOTIFICATION_INTERVAL_NANOS = TimeUnit.MILLISECONDS.toNanos(250L);
    private static final Comparator<DownloadRecord> DISPLAY_ORDER =
            Comparator.comparing((DownloadRecord record) -> record.status == DownloadStatus.DOWNLOADING).reversed()
                    .thenComparing((DownloadRecord record) -> record.priority, Comparator.reverseOrder())
                    .thenComparingLong(record -> record.queueOrder)
                    .thenComparing(record -> record.id);
    private static final Comparator<DownloadRecord> QUEUE_ORDER =
            Comparator.comparing((DownloadRecord record) -> record.priority, Comparator.reverseOrder())
                    .thenComparingLong(record -> record.queueOrder)
                    .thenComparing(record -> record.id);

    private final SourceRegistry sources;
    private final LibraryCatalog library;
    private final Path defaultStorageRoot;
    private Path storageRoot;
    private Path contentRoot;
    private DownloadStoragePolicy policy;
    private final Clock clock;
    private final FileDownloadQueueStore store;
    private final FileDownloadStorageLocationStore locationStore;
    private final FileDownloadStoragePolicyStore storagePolicyStore;
    private final FileAutomaticDownloadPolicyStore automaticPolicyStore;
    private final ExecutorService workers;
    private final BooleanSupplier largeTransfersAllowed;
    private final VideoDownloadPlanner videoPlanner;
    private final VideoDownloadFinalizer videoFinalizer;
    private final Map<DownloadId, DownloadRecord> records = new LinkedHashMap<>();
    private final Set<DownloadId> scheduled = new HashSet<>();
    private final Set<DownloadId> deferredDeletions = new HashSet<>();
    private final Map<DownloadId, DownloadRecord> deferredRemovals = new LinkedHashMap<>();
    private final Set<Runnable> listeners = new HashSet<>();
    private long usedStorageBytes;
    private long nextQueueOrder;
    private AutomaticDownloadPolicy automaticPolicy;
    private boolean offlineMode;
    private boolean closed;

    public DefaultDownloadService(
            SourceRegistry sources,
            LibraryCatalog library,
            Path root,
            DownloadStoragePolicy policy) {
        this(sources, library, root, policy, () -> true, unavailableHttpClient());
    }

    public DefaultDownloadService(
            SourceRegistry sources,
            LibraryCatalog library,
            Path root,
            DownloadStoragePolicy policy,
            BooleanSupplier largeTransfersAllowed) {
        this(sources, library, root, policy, largeTransfersAllowed, unavailableHttpClient());
    }

    public DefaultDownloadService(
            SourceRegistry sources,
            LibraryCatalog library,
            Path root,
            DownloadStoragePolicy policy,
            BooleanSupplier largeTransfersAllowed,
            AnilibHttpClient httpClient) {
        this(sources, library, root, policy, largeTransfersAllowed, httpClient, VideoDownloadFinalizer.unavailable());
    }

    public DefaultDownloadService(
            SourceRegistry sources,
            LibraryCatalog library,
            Path root,
            DownloadStoragePolicy policy,
            BooleanSupplier largeTransfersAllowed,
            AnilibHttpClient httpClient,
            VideoDownloadFinalizer videoFinalizer) {
        this(
                sources,
                library,
                root,
                policy,
                Clock.systemUTC(),
                ManagedExecutors.fixed("anilib-download", policy.concurrentJobs()),
                largeTransfersAllowed,
                httpClient,
                videoFinalizer);
    }

    DefaultDownloadService(
            SourceRegistry sources,
            LibraryCatalog library,
            Path root,
            DownloadStoragePolicy policy,
            Clock clock,
            ExecutorService workers,
            BooleanSupplier largeTransfersAllowed) {
        this(
                sources,
                library,
                root,
                policy,
                clock,
                workers,
                largeTransfersAllowed,
                unavailableHttpClient(),
                VideoDownloadFinalizer.unavailable());
    }

    DefaultDownloadService(
            SourceRegistry sources,
            LibraryCatalog library,
            Path root,
            DownloadStoragePolicy policy,
            Clock clock,
            ExecutorService workers,
            BooleanSupplier largeTransfersAllowed,
            AnilibHttpClient httpClient) {
        this(
                sources,
                library,
                root,
                policy,
                clock,
                workers,
                largeTransfersAllowed,
                httpClient,
                VideoDownloadFinalizer.unavailable());
    }

    DefaultDownloadService(
            SourceRegistry sources,
            LibraryCatalog library,
            Path root,
            DownloadStoragePolicy policy,
            Clock clock,
            ExecutorService workers,
            BooleanSupplier largeTransfersAllowed,
            AnilibHttpClient httpClient,
            VideoDownloadFinalizer videoFinalizer) {
        this.sources = Objects.requireNonNull(sources, "sources must not be null");
        this.library = Objects.requireNonNull(library, "library must not be null");
        Path normalizedRoot = Objects.requireNonNull(root, "root must not be null").toAbsolutePath().normalize();
        this.defaultStorageRoot = normalizedRoot;
        this.storageRoot = normalizedRoot;
        this.contentRoot = normalizedRoot.resolve("content");
        this.policy = Objects.requireNonNull(policy, "policy must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.workers = Objects.requireNonNull(workers, "workers must not be null");
        this.largeTransfersAllowed = Objects.requireNonNull(
                largeTransfersAllowed,
                "largeTransfersAllowed must not be null");
        this.videoPlanner = new VideoDownloadPlanner(Objects.requireNonNull(
                httpClient,
                "httpClient must not be null"));
        this.videoFinalizer = Objects.requireNonNull(videoFinalizer, "videoFinalizer must not be null");
        this.store = new FileDownloadQueueStore(normalizedRoot.resolve("queue.anilib"));
        this.locationStore = new FileDownloadStorageLocationStore(normalizedRoot.resolve("storage-location.anilib"));
        this.storagePolicyStore = new FileDownloadStoragePolicyStore(
                normalizedRoot.resolve("storage-policy.anilib"));
        this.automaticPolicyStore = new FileAutomaticDownloadPolicyStore(
                normalizedRoot.resolve("automatic-downloads.anilib"));
        load();
    }

    @Override
    public synchronized DownloadQueueSnapshot snapshot() {
        ensureOpen();
        List<DownloadRecord> ordered = records.values().stream().sorted(DISPLAY_ORDER).toList();
        List<DownloadRecord> queueOrder = records.values().stream()
                .sorted(Comparator.comparingLong(record -> record.queueOrder))
                .toList();
        Map<DownloadId, Integer> positions = new LinkedHashMap<>();
        for (int index = 0; index < queueOrder.size(); index++) {
            positions.put(queueOrder.get(index).id, index);
        }
        List<DownloadJobSnapshot> jobs = ordered.stream()
                .map(record -> record.snapshot(positions.get(record.id)))
                .toList();
        return new DownloadQueueSnapshot(
                jobs,
                offlineMode,
                usedStorageBytes,
                policy.maximumStorageBytes());
    }

    @Override
    public synchronized boolean canEnqueue(LibraryItemId libraryItemId) {
        Objects.requireNonNull(libraryItemId, "libraryItemId must not be null");
        ensureOpen();
        if (offlineMode || !largeTransfersAllowed.getAsBoolean()) {
            return false;
        }
        return library.find(libraryItemId).flatMap(item -> item.origin()
                        .flatMap(origin -> sources.find(SourceId.of(origin.sourceId())))
                        .filter(source -> item.kind() == MediaKind.ANIME
                                ? source instanceof StreamingSource
                                : source instanceof PagedSource))
                .isPresent();
    }

    @Override
    public synchronized DownloadId enqueue(LibraryItemId libraryItemId) {
        LibraryItem item = library.find(Objects.requireNonNull(
                        libraryItemId,
                        "libraryItemId must not be null"))
                .orElseThrow(() -> new DownloadException("Library item was not found"));
        return item.kind() == MediaKind.ANIME
                ? enqueueVideo(item, null)
                : enqueue(libraryItemId, (SourceContentUnitId) null);
    }

    @Override
    public synchronized DownloadId enqueue(
            LibraryItemId libraryItemId,
            SourceContentUnitId contentUnitId) {
        Objects.requireNonNull(libraryItemId, "libraryItemId must not be null");
        ensureOpen();
        if (offlineMode) {
            throw new DownloadException("Downloads cannot be queued while offline mode is enabled");
        }
        ensureLargeTransfersAllowed();
        LibraryItem item = library.find(libraryItemId)
                .orElseThrow(() -> new DownloadException("Library item was not found"));
        LibraryOrigin origin = item.origin()
                .orElseThrow(() -> new DownloadException("Library item has no source origin"));
        SourceCatalogueItemId itemId = new SourceCatalogueItemId(
                SourceId.of(origin.sourceId()),
                origin.sourceItemKey());
        PagedSource source = pagedSource(itemId.sourceId());
        List<SourceContentUnit> units = validatedUnits(source, itemId);
        SourceContentUnit unit = contentUnitId == null
                ? selectUnit(item, units)
                : units.stream()
                        .filter(candidate -> candidate.id().equals(contentUnitId))
                        .findFirst()
                        .orElseThrow(() -> new DownloadException("Content unit was not found for this title"));
        List<SourcePageResource> pages = validatedPages(source, unit);
        records.values().stream()
                .filter(record -> record.contentUnit.id().equals(unit.id()))
                .filter(record -> record.status != DownloadStatus.CANCELLED)
                .findFirst()
                .ifPresent(record -> {
                    throw new DownloadException("This content unit is already in the download queue");
                });
        long estimatedBytes = pages.stream()
                .mapToLong(SourcePageResource::estimatedBytes)
                .filter(size -> size >= 0)
                .sum();
        boolean allSizesKnown = pages.stream()
                .noneMatch(page -> page.estimatedBytes() == SourcePageResource.UNKNOWN_SIZE);
        if (allSizesKnown && usedStorageBytes + estimatedBytes > policy.maximumStorageBytes()) {
            throw new DownloadException("The download would exceed the configured storage limit");
        }
        Instant now = clock.instant();
        DownloadRecord record = new DownloadRecord(
                DownloadId.create(),
                item.id(),
                item.title(),
                itemId,
                unit,
                pages,
                null,
                DownloadPriority.NORMAL,
                nextQueueOrder++,
                DownloadStatus.QUEUED,
                0,
                0,
                null,
                now);
        records.put(record.id, record);
        persist();
        notifyListeners();
        schedule(record);
        return record.id;
    }

    @Override
    public synchronized DownloadId enqueue(LibraryItemId libraryItemId, String sourceContentId) {
        Objects.requireNonNull(libraryItemId, "libraryItemId must not be null");
        if (sourceContentId == null || sourceContentId.isBlank()) {
            throw new IllegalArgumentException("sourceContentId must not be blank");
        }
        LibraryItem item = library.find(libraryItemId)
                .orElseThrow(() -> new DownloadException("Library item was not found"));
        LibraryOrigin origin = item.origin()
                .orElseThrow(() -> new DownloadException("Library item has no source origin"));
        SourceCatalogueItemId itemId = new SourceCatalogueItemId(
                SourceId.of(origin.sourceId()),
                origin.sourceItemKey());
        return item.kind() == MediaKind.ANIME
                ? enqueueVideo(item, new SourceEpisodeId(itemId, sourceContentId))
                : enqueue(libraryItemId, new SourceContentUnitId(itemId, sourceContentId));
    }

    private DownloadId enqueueVideo(LibraryItem item, SourceEpisodeId episodeId) {
        ensureOpen();
        if (offlineMode) {
            throw new DownloadException("Downloads cannot be queued while offline mode is enabled");
        }
        ensureLargeTransfersAllowed();
        LibraryOrigin origin = item.origin()
                .orElseThrow(() -> new DownloadException("Library item has no source origin"));
        SourceCatalogueItemId itemId = new SourceCatalogueItemId(
                SourceId.of(origin.sourceId()),
                origin.sourceItemKey());
        StreamingSource source = streamingSource(itemId.sourceId());
        List<SourceEpisode> episodes = validatedEpisodes(source, itemId);
        SourceEpisode episode = episodeId == null
                ? selectEpisode(item, episodes)
                : episodes.stream()
                        .filter(candidate -> candidate.id().equals(episodeId))
                        .findFirst()
                        .orElseThrow(() -> new DownloadException("Episode was not found for this title"));
        List<SourceVideoStream> streams = validatedStreams(source, episode.id());
        SourceVideoStream stream = streams.stream()
                .filter(candidate -> candidate.format() != SourceStreamFormat.DASH)
                .findFirst()
                .orElse(streams.getFirst());
        SourceContentUnitId unitId = new SourceContentUnitId(itemId, episode.id().value());
        SourceContentUnit unit = new SourceContentUnit(
                unitId,
                episode.title(),
                episode.episodeNumber(),
                episode.uploadedAt());
        records.values().stream()
                .filter(record -> record.contentUnit.id().equals(unitId))
                .filter(record -> record.status != DownloadStatus.CANCELLED)
                .findFirst()
                .ifPresent(record -> {
                    throw new DownloadException("This episode is already in the download queue");
                });
        VideoDownloadPlan plan = videoPlanner.plan(unitId, stream);
        long estimatedBytes = plan.resources().stream()
                .mapToLong(SourcePageResource::estimatedBytes)
                .filter(size -> size >= 0L)
                .sum();
        boolean allSizesKnown = plan.resources().stream()
                .noneMatch(resource -> resource.estimatedBytes() == SourcePageResource.UNKNOWN_SIZE);
        if (allSizesKnown && usedStorageBytes + estimatedBytes > policy.maximumStorageBytes()) {
            throw new DownloadException("The download would exceed the configured storage limit");
        }
        DownloadRecord record = new DownloadRecord(
                DownloadId.create(),
                item.id(),
                item.title(),
                itemId,
                unit,
                plan.resources(),
                plan.metadata(),
                DownloadPriority.NORMAL,
                nextQueueOrder++,
                DownloadStatus.QUEUED,
                0,
                0L,
                null,
                clock.instant());
        records.put(record.id, record);
        persist();
        notifyListeners();
        schedule(record);
        return record.id;
    }

    @Override
    public synchronized void pause(DownloadId id) {
        DownloadRecord record = record(id);
        if (record.status == DownloadStatus.QUEUED || record.status == DownloadStatus.DOWNLOADING) {
            transition(record, DownloadStatus.PAUSED, null);
        }
    }

    @Override
    public synchronized void resume(DownloadId id) {
        DownloadRecord record = record(id);
        ensureOnline();
        if (record.status == DownloadStatus.PAUSED || record.status == DownloadStatus.FAILED) {
            transition(record, DownloadStatus.QUEUED, null);
            schedule(record);
        }
    }

    @Override
    public synchronized void cancel(DownloadId id) {
        DownloadRecord record = record(id);
        if (record.status != DownloadStatus.COMPLETED && record.status != DownloadStatus.CANCELLED) {
            record.status = DownloadStatus.CANCELLED;
            record.error = null;
            record.updatedAt = clock.instant();
            if (policy.removePartialOnCancel()) {
                if (scheduled.contains(record.id)) {
                    deferredDeletions.add(record.id);
                } else {
                    deleteFiles(record);
                    resetDownloadedProgress(record);
                }
            }
            persist();
            notifyListeners();
        }
    }

    @Override
    public synchronized void remove(DownloadId id) {
        DownloadRecord record = record(id);
        record.status = DownloadStatus.CANCELLED;
        record.error = null;
        record.updatedAt = clock.instant();
        removeRecordSafely(record);
        persist();
        notifyListeners();
    }

    @Override
    public synchronized void removeAll() {
        ensureOpen();
        if (records.isEmpty()) {
            return;
        }
        List<DownloadRecord> removed = List.copyOf(records.values());
        removed.forEach(record -> record.status = DownloadStatus.CANCELLED);
        removed.forEach(this::removeRecordSafely);
        persist();
        notifyListeners();
    }

    @Override
    public synchronized void setPriority(DownloadId id, DownloadPriority priority) {
        DownloadRecord record = record(id);
        DownloadPriority requested = Objects.requireNonNull(priority, "priority must not be null");
        if (record.priority != requested) {
            record.priority = requested;
            record.updatedAt = clock.instant();
            persist();
            notifyListeners();
            schedule(record);
        }
    }

    @Override
    public synchronized void move(DownloadId id, int queuePosition) {
        DownloadRecord selected = record(id);
        List<DownloadRecord> ordered = records.values().stream()
                .sorted(Comparator.comparingLong(record -> record.queueOrder))
                .collect(Collectors.toCollection(ArrayList::new));
        ordered.remove(selected);
        ordered.add(Math.max(0, Math.min(queuePosition, ordered.size())), selected);
        for (int index = 0; index < ordered.size(); index++) {
            ordered.get(index).queueOrder = index;
        }
        nextQueueOrder = ordered.size();
        selected.updatedAt = clock.instant();
        persist();
        notifyListeners();
        schedule(selected);
    }

    @Override
    public synchronized void retry(DownloadId id, DownloadRecoveryMode mode) {
        DownloadRecord record = record(id);
        DownloadRecoveryMode requested = Objects.requireNonNull(mode, "mode must not be null");
        ensureOnline();
        if (record.status != DownloadStatus.FAILED
                && record.status != DownloadStatus.CANCELLED
                && record.status != DownloadStatus.PAUSED) {
            throw new DownloadException("Only failed, cancelled, or paused downloads can be retried");
        }
        if (requested == DownloadRecoveryMode.RESTART) {
            deleteFiles(record);
            record.completedPages = 0;
            record.downloadedBytes = 0L;
        }
        transition(record, DownloadStatus.QUEUED, null);
        schedule(record);
    }

    @Override
    public synchronized DownloadStorageSnapshot storage() {
        ensureOpen();
        try {
            return new DownloadStorageSnapshot(
                    storageRoot,
                    !storageRoot.equals(defaultStorageRoot),
                    Files.isWritable(contentRoot),
                    Files.getFileStore(contentRoot).getUsableSpace());
        } catch (IOException exception) {
            return new DownloadStorageSnapshot(
                    storageRoot,
                    !storageRoot.equals(defaultStorageRoot),
                    false,
                    0L);
        }
    }

    @Override
    public synchronized void configureMaximumStorageBytes(long maximumStorageBytes) {
        ensureOpen();
        if (maximumStorageBytes < usedStorageBytes) {
            throw new DownloadException("The storage limit cannot be lower than downloaded data");
        }
        DownloadStoragePolicy requested;
        try {
            requested = policy.withMaximumStorageBytes(maximumStorageBytes);
        } catch (IllegalArgumentException exception) {
            throw new DownloadException("Invalid download storage limit", exception);
        }
        try {
            storagePolicyStore.save(maximumStorageBytes);
        } catch (IOException exception) {
            throw new DownloadException("Unable to persist the download storage limit", exception);
        }
        policy = requested;
        notifyListeners();
    }

    @Override
    public synchronized void changeStorageLocation(Path location) {
        ensureOpen();
        Path targetRoot = validateStorageRoot(location);
        Path targetContent = targetRoot.resolve("content").normalize();
        if (targetContent.equals(contentRoot)) {
            return;
        }
        if (targetContent.startsWith(contentRoot) || contentRoot.startsWith(targetContent)) {
            throw new DownloadException("Download storage locations must not contain each other");
        }
        Map<DownloadId, DownloadStatus> previousStatuses = new LinkedHashMap<>();
        records.values().forEach(record -> {
            previousStatuses.put(record.id, record.status);
            if (record.status == DownloadStatus.DOWNLOADING || record.status == DownloadStatus.QUEUED) {
                record.status = DownloadStatus.PAUSED;
            }
        });
        Path previousContent = contentRoot;
        try {
            copyManagedContent(previousContent, targetContent);
            locationStore.save(targetRoot);
            storageRoot = targetRoot;
            contentRoot = targetContent;
            restoreMigratedStatuses(previousStatuses);
            persist();
            notifyListeners();
            cleanupMigratedContent(previousContent);
            scheduleAvailable();
        } catch (IOException | RuntimeException exception) {
            previousStatuses.forEach((id, status) -> {
                DownloadRecord record = records.get(id);
                if (record != null) {
                    record.status = status;
                }
            });
            throw exception instanceof DownloadException downloadException
                    ? downloadException
                    : new DownloadException("Unable to migrate download storage", exception);
        }
    }

    @Override
    public synchronized DownloadIndexRepairResult repairIndex() {
        ensureOpen();
        int repaired = 0;
        int orphaned = 0;
        try {
            usedStorageBytes = 0L;
            for (DownloadRecord record : records.values()) {
                DownloadStatus status = record.status;
                int pages = record.completedPages;
                long bytes = record.downloadedBytes;
                String error = record.error;
                reconcile(record);
                if (status != record.status || pages != record.completedPages
                        || bytes != record.downloadedBytes || !Objects.equals(error, record.error)) {
                    repaired++;
                }
            }
            Set<String> known = records.keySet().stream().map(DownloadId::toString).collect(
                    Collectors.toSet());
            try (Stream<Path> entries = Files.list(contentRoot)) {
                for (Path entry : entries.filter(Files::isDirectory).toList()) {
                    if (!known.contains(entry.getFileName().toString()) && isDownloadDirectory(entry)) {
                        deleteDirectory(entry, false);
                        orphaned++;
                    }
                }
            }
            persist();
            notifyListeners();
            scheduleAvailable();
            return new DownloadIndexRepairResult(repaired, orphaned, usedStorageBytes);
        } catch (IOException exception) {
            throw new DownloadException("Unable to repair download index", exception);
        }
    }

    @Override
    public synchronized AutomaticDownloadPolicy automaticPolicy() {
        ensureOpen();
        return automaticPolicy;
    }

    @Override
    public synchronized void configureAutomaticDownloads(AutomaticDownloadPolicy nextPolicy) {
        ensureOpen();
        automaticPolicy = Objects.requireNonNull(nextPolicy, "policy must not be null");
        persistAutomaticPolicy();
        notifyListeners();
        if (automaticPolicy.enabled()) {
            synchronizeAutomaticDownloads();
        } else {
            cleanAutomaticDownloads();
        }
    }

    @Override
    public synchronized AutomaticDownloadResult synchronizeAutomaticDownloads() {
        ensureOpen();
        int removed = cleanAutomaticDownloads();
        if (!automaticPolicy.enabled() || offlineMode || !largeTransfersAllowed.getAsBoolean()) {
            return new AutomaticDownloadResult(0, removed, List.of());
        }
        int enqueued = 0;
        List<String> failures = new ArrayList<>();
        for (LibraryItem item : library.snapshot()) {
            int limit = automaticLimit(item);
            if (limit == 0 || automaticPolicy.favoritesOnly() && !item.favorite()) {
                continue;
            }
            try {
                LibraryOrigin origin = item.origin().orElseThrow(
                        () -> new DownloadException("Title has no source origin"));
                SourceCatalogueItemId itemId = new SourceCatalogueItemId(
                        SourceId.of(origin.sourceId()),
                        origin.sourceItemKey());
                enqueued += item.kind() == MediaKind.ANIME
                        ? enqueueAutomaticEpisodes(item, itemId, limit, failures)
                        : enqueueAutomaticChapters(item, itemId, limit, failures);
            } catch (DownloadException exception) {
                failures.add(item.title() + ": " + message(exception));
            }
        }
        return new AutomaticDownloadResult(enqueued, removed, failures);
    }

    private int enqueueAutomaticChapters(
            LibraryItem item,
            SourceCatalogueItemId itemId,
            int limit,
            List<String> failures) {
        PagedSource source = pagedSource(itemId.sourceId());
        List<SourceContentUnit> units = validatedUnits(source, itemId);
        int first = Math.max(0, units.size() - limit);
        int enqueued = 0;
        for (SourceContentUnit unit : units.subList(first, units.size())) {
            if (!hasDownload(unit.id())) {
                try {
                    enqueue(item.id(), unit.id());
                    enqueued++;
                } catch (DownloadException exception) {
                    failures.add(item.title() + " · " + unit.title() + ": " + message(exception));
                }
            }
        }
        return enqueued;
    }

    private int enqueueAutomaticEpisodes(
            LibraryItem item,
            SourceCatalogueItemId itemId,
            int limit,
            List<String> failures) {
        StreamingSource source = streamingSource(itemId.sourceId());
        List<SourceEpisode> episodes = validatedEpisodes(source, itemId);
        int enqueued = 0;
        for (SourceEpisode episode : episodes.subList(0, Math.min(limit, episodes.size()))) {
            SourceContentUnitId unitId = new SourceContentUnitId(itemId, episode.id().value());
            if (!hasDownload(unitId)) {
                try {
                    enqueue(item.id(), episode.id().value());
                    enqueued++;
                } catch (DownloadException exception) {
                    failures.add(item.title() + " · " + episode.title() + ": " + message(exception));
                }
            }
        }
        return enqueued;
    }

    @Override
    public synchronized int cleanAutomaticDownloads() {
        ensureOpen();
        if (automaticPolicy.cleanupPolicy() == DownloadCleanupPolicy.KEEP_ALL) {
            return 0;
        }
        List<DownloadRecord> removable = new ArrayList<>();
        Map<LibraryItemId, List<DownloadRecord>> completedByTitle = new LinkedHashMap<>();
        records.values().stream()
                .filter(record -> record.status == DownloadStatus.COMPLETED)
                .forEach(record -> completedByTitle
                        .computeIfAbsent(record.libraryItemId, ignored -> new ArrayList<>())
                        .add(record));
        if (automaticPolicy.cleanupPolicy() == DownloadCleanupPolicy.KEEP_LATEST) {
            completedByTitle.values().forEach(completed -> {
                completed.sort(Comparator
                        .comparing((DownloadRecord record) ->
                                record.contentUnit.publishedAt().orElse(Instant.MIN))
                        .thenComparing(record -> record.updatedAt)
                        .reversed());
                removable.addAll(completed.stream()
                        .skip(automaticPolicy.retainedCompletedPerTitle())
                        .toList());
            });
        } else {
            completedByTitle.forEach((itemId, completed) -> library.find(itemId)
                    .flatMap(LibraryItem::progress)
                    .filter(progress -> progress.completion().orElse(0.0D) >= 1.0D)
                    .ifPresent(progress -> completed.stream()
                            .filter(record -> record.contentUnit.id().value().equals(progress.contentId()))
                            .forEach(removable::add)));
        }
        removeRecords(removable);
        return removable.size();
    }

    @Override
    public synchronized void pauseTitle(LibraryItemId libraryItemId) {
        Objects.requireNonNull(libraryItemId, "libraryItemId must not be null");
        ensureOpen();
        boolean changed = false;
        for (DownloadRecord record : records.values()) {
            if (record.libraryItemId.equals(libraryItemId)
                    && (record.status == DownloadStatus.QUEUED
                    || record.status == DownloadStatus.DOWNLOADING)) {
                record.status = DownloadStatus.PAUSED;
                record.updatedAt = clock.instant();
                changed = true;
            }
        }
        finishBulkChange(changed);
    }

    @Override
    public synchronized void resumeTitle(LibraryItemId libraryItemId) {
        Objects.requireNonNull(libraryItemId, "libraryItemId must not be null");
        ensureOnline();
        boolean changed = false;
        for (DownloadRecord record : records.values()) {
            if (record.libraryItemId.equals(libraryItemId)
                    && (record.status == DownloadStatus.PAUSED || record.status == DownloadStatus.FAILED)) {
                record.status = DownloadStatus.QUEUED;
                record.error = null;
                record.updatedAt = clock.instant();
                changed = true;
            }
        }
        finishBulkChange(changed);
        scheduleAvailable();
    }

    @Override
    public synchronized void removeTitle(LibraryItemId libraryItemId) {
        Objects.requireNonNull(libraryItemId, "libraryItemId must not be null");
        ensureOpen();
        List<DownloadRecord> removed = records.values().stream()
                .filter(record -> record.libraryItemId.equals(libraryItemId))
                .toList();
        removed.forEach(record -> record.status = DownloadStatus.CANCELLED);
        removed.forEach(this::removeRecordSafely);
        finishBulkChange(!removed.isEmpty());
    }

    @Override
    public synchronized void pauseAll() {
        ensureOpen();
        boolean changed = false;
        for (DownloadRecord record : records.values()) {
            if (record.status == DownloadStatus.QUEUED || record.status == DownloadStatus.DOWNLOADING) {
                record.status = DownloadStatus.PAUSED;
                record.updatedAt = clock.instant();
                changed = true;
            }
        }
        if (changed) {
            persist();
            notifyListeners();
        }
    }

    @Override
    public synchronized void resumeAll() {
        ensureOpen();
        ensureOnline();
        List<DownloadRecord> resumable = records.values().stream()
                .filter(record -> record.status == DownloadStatus.PAUSED || record.status == DownloadStatus.FAILED)
                .toList();
        if (!resumable.isEmpty()) {
            Instant now = clock.instant();
            resumable.forEach(record -> {
                record.status = DownloadStatus.QUEUED;
                record.error = null;
                record.updatedAt = now;
            });
            persist();
            notifyListeners();
            resumable.forEach(this::schedule);
        }
    }

    @Override
    public synchronized void setOfflineMode(boolean enabled) {
        ensureOpen();
        if (offlineMode == enabled) {
            return;
        }
        offlineMode = enabled;
        if (enabled) {
            for (DownloadRecord record : records.values()) {
                if (record.status == DownloadStatus.QUEUED || record.status == DownloadStatus.DOWNLOADING) {
                    record.status = DownloadStatus.PAUSED;
                    record.updatedAt = clock.instant();
                }
            }
        }
        persist();
        notifyListeners();
    }

    public synchronized int cleanUnusedData() {
        ensureOpen();
        List<DownloadRecord> orphaned = records.values().stream()
                .filter(record -> library.find(record.libraryItemId).isEmpty())
                .toList();
        if (orphaned.isEmpty()) {
            return 0;
        }
        orphaned.forEach(record -> {
            record.status = DownloadStatus.CANCELLED;
            removeRecordSafely(record);
        });
        persist();
        notifyListeners();
        return orphaned.size();
    }

    @Override
    public synchronized AutoCloseable observe(Runnable listener) {
        Objects.requireNonNull(listener, "listener must not be null");
        ensureOpen();
        listeners.add(listener);
        return () -> removeListener(listener);
    }

    @Override
    public synchronized Optional<ReaderContent> find(
            SourceCatalogueItemId itemId,
            Optional<String> preferredContentId) {
        Objects.requireNonNull(itemId, "itemId must not be null");
        Objects.requireNonNull(preferredContentId, "preferredContentId must not be null");
        ensureOpen();
        List<DownloadRecord> complete = records.values().stream()
                .filter(record -> record.status == DownloadStatus.COMPLETED)
                .filter(record -> record.sourceItemId.equals(itemId))
                .sorted(Comparator.comparing((DownloadRecord record) -> record.updatedAt).reversed())
                .toList();
        if (preferredContentId.isPresent()) {
            Optional<DownloadRecord> preferred = complete.stream()
                    .filter(record -> record.contentUnit.id().value().equals(preferredContentId.orElseThrow()))
                    .findFirst();
            if (preferred.isPresent()) {
                DownloadRecord record = preferred.orElseThrow();
                return Optional.of(new ReaderContent(record.contentUnit, record.pages));
            }
        }
        return complete.stream().findFirst().map(record -> new ReaderContent(record.contentUnit, record.pages));
    }

    @Override
    public synchronized List<SourceEpisode> episodes(SourceCatalogueItemId itemId) {
        Objects.requireNonNull(itemId, "itemId must not be null");
        ensureOpen();
        return records.values().stream()
                .filter(DownloadRecord::video)
                .filter(record -> record.status == DownloadStatus.COMPLETED)
                .filter(record -> record.sourceItemId.equals(itemId))
                .map(record -> new SourceEpisode(
                        new SourceEpisodeId(itemId, record.contentUnit.id().value()),
                        record.contentUnit.title(),
                        record.contentUnit.number(),
                        record.contentUnit.publishedAt(),
                        Optional.empty()))
                .sorted(Comparator.comparingDouble(SourceEpisode::episodeNumber).reversed())
                .toList();
    }

    @Override
    public synchronized List<SourceVideoStream> streams(SourceEpisodeId episodeId) {
        Objects.requireNonNull(episodeId, "episodeId must not be null");
        ensureOpen();
        return records.values().stream()
                .filter(DownloadRecord::video)
                .filter(record -> record.status == DownloadStatus.COMPLETED)
                .filter(record -> record.sourceItemId.equals(episodeId.itemId()))
                .filter(record -> record.contentUnit.id().value().equals(episodeId.value()))
                .max(Comparator.comparing(record -> record.updatedAt))
                .map(record -> {
                    Path finalized = finalizedVideoFile(record);
                    boolean hasFinalizedVideo = Files.isRegularFile(finalized);
                    return List.of(new SourceVideoStream(
                            "offline-" + record.id,
                            "Offline",
                            (hasFinalizedVideo
                                    ? finalized
                                    : record.video.hls() ? playlistFile(record) : videoFile(record)).toUri(),
                            hasFinalizedVideo ? SourceStreamFormat.PROGRESSIVE : record.video.format(),
                            Map.of(),
                            List.of()));
                })
                .orElseGet(List::of);
    }

    @Override
    public synchronized byte[] readPage(SourcePageResource page) {
        Objects.requireNonNull(page, "page must not be null");
        ensureOpen();
        DownloadRecord record = records.values().stream()
                .filter(candidate -> candidate.status == DownloadStatus.COMPLETED)
                .filter(candidate -> candidate.contentUnit.id().equals(page.contentUnitId()))
                .filter(candidate -> page.index() < candidate.pages.size())
                .filter(candidate -> candidate.pages.get(page.index()).equals(page))
                .findFirst()
                .orElseThrow(() -> new DownloadException("Downloaded page is not available"));
        try {
            return Files.readAllBytes(pageFile(record, page.index()));
        } catch (IOException exception) {
            throw new DownloadException("Unable to read downloaded page", exception);
        }
    }

    @Override
    public synchronized boolean sourceFallbackAllowed() {
        ensureOpen();
        return !offlineMode;
    }

    private void run(DownloadId id) {
        try {
            DownloadRecord record;
            synchronized (this) {
                record = records.get(id);
                if (closed || offlineMode || !largeTransfersAllowed.getAsBoolean()
                        || record == null || record.status != DownloadStatus.QUEUED) {
                    return;
                }
                transition(record, DownloadStatus.DOWNLOADING, null);
                record.activeStartedNanos = System.nanoTime();
                record.activeStartBytes = record.downloadedBytes;
            }
            PagedSource source = record.video() ? null : pagedSource(record.sourceItemId.sourceId());
            while (record.video() ? downloadNextVideoResource(record) : downloadNextPage(record, source)) {
                // Continue until paused, failed, cancelled, or complete.
            }
            if (record.video()) {
                finalizeVideo(record);
            }
        } catch (RuntimeException exception) {
            synchronized (this) {
                DownloadRecord record = records.get(id);
                if (!closed
                        && record != null
                        && (record.status == DownloadStatus.DOWNLOADING
                        || record.status == DownloadStatus.QUEUED)) {
                    fail(record, exception);
                }
            }
        } finally {
            synchronized (this) {
                scheduled.remove(id);
                try {
                    completeDeferredCleanup(id);
                } finally {
                    scheduleAvailable();
                }
            }
        }
    }

    private boolean downloadNextPage(DownloadRecord record, PagedSource source) {
        SourcePageResource page;
        synchronized (this) {
            if (closed || offlineMode || records.get(record.id) != record
                    || record.status != DownloadStatus.DOWNLOADING) {
                return false;
            }
            if (!largeTransfersAllowed.getAsBoolean()) {
                transition(record, DownloadStatus.PAUSED, null);
                return false;
            }
            if (record.completedPages == record.pages.size()) {
                transition(record, DownloadStatus.COMPLETED, null);
                return false;
            }
            page = record.pages.get(record.completedPages);
            if (page.estimatedBytes() > policy.maximumPageBytes()) {
                throw new DownloadException("Page exceeds the configured per-page storage limit");
            }
        }
        byte[] bytes = Objects.requireNonNull(source.readPage(page), "paged source returned null page bytes");
        synchronized (this) {
            if (closed || offlineMode || records.get(record.id) != record
                    || record.status != DownloadStatus.DOWNLOADING) {
                return false;
            }
            if (!largeTransfersAllowed.getAsBoolean()) {
                transition(record, DownloadStatus.PAUSED, null);
                return false;
            }
            if (bytes.length > policy.maximumPageBytes()) {
                throw new DownloadException("Page exceeds the configured per-page storage limit");
            }
            if (usedStorageBytes + bytes.length > policy.maximumStorageBytes()) {
                throw new DownloadException("Download storage limit reached");
            }
            writePage(record, page.index(), bytes);
            record.completedPages++;
            record.downloadedBytes += bytes.length;
            usedStorageBytes += bytes.length;
            long elapsedNanos = Math.max(1L, System.nanoTime() - record.activeStartedNanos);
            long transferred = record.downloadedBytes - record.activeStartBytes;
            record.bytesPerSecond = saturatedRate(transferred, elapsedNanos);
            record.updatedAt = clock.instant();
            if (record.completedPages == record.pages.size()) {
                record.status = DownloadStatus.COMPLETED;
            }
            if (record.status == DownloadStatus.COMPLETED) {
                persist();
                notifyListeners();
                return false;
            }
            publishProgress(record);
            return true;
        }
    }

    private boolean downloadNextVideoResource(DownloadRecord record) {
        SourcePageResource resource;
        synchronized (this) {
            if (!readyToTransfer(record)) {
                return false;
            }
            if (record.completedPages == record.pages.size()) {
                completeVideo(record);
                return false;
            }
            resource = record.pages.get(record.completedPages);
            if (resource.estimatedBytes() > policy.maximumPageBytes()) {
                throw new DownloadException("Video chunk exceeds the configured transfer limit");
            }
        }
        VideoDownloadResource videoResource = VideoDownloadResource.decode(resource.value());
        byte[] bytes = videoPlanner.fetch(record.video, videoResource);
        synchronized (this) {
            if (!readyToTransfer(record)) {
                return false;
            }
            if (bytes.length > policy.maximumPageBytes()) {
                throw new DownloadException("Video chunk exceeds the configured transfer limit");
            }
            if (usedStorageBytes + bytes.length > policy.maximumStorageBytes()) {
                throw new DownloadException("Download storage limit reached");
            }
            if (record.video.hls()) {
                writePage(record, resource.index(), bytes);
            } else {
                appendVideoChunk(record, videoResource, bytes);
            }
            record.completedPages++;
            record.downloadedBytes += bytes.length;
            usedStorageBytes += bytes.length;
            updateTransferRate(record);
            if (record.completedPages == record.pages.size()) {
                completeVideo(record);
            } else {
                publishProgress(record);
            }
            return record.status == DownloadStatus.DOWNLOADING
                    && record.completedPages < record.pages.size();
        }
    }

    private boolean readyToTransfer(DownloadRecord record) {
        if (closed || offlineMode || records.get(record.id) != record
                || record.status != DownloadStatus.DOWNLOADING) {
            return false;
        }
        if (!largeTransfersAllowed.getAsBoolean()) {
            transition(record, DownloadStatus.PAUSED, null);
            return false;
        }
        return true;
    }

    private void updateTransferRate(DownloadRecord record) {
        long elapsedNanos = Math.max(1L, System.nanoTime() - record.activeStartedNanos);
        long transferred = record.downloadedBytes - record.activeStartBytes;
        record.bytesPerSecond = saturatedRate(transferred, elapsedNanos);
        record.updatedAt = clock.instant();
    }

    private void completeVideo(DownloadRecord record) {
        if (record.video.hls()) {
            writeOfflinePlaylist(record);
        }
        if (videoFinalizer.available()) {
            record.bytesPerSecond = 0L;
            record.error = null;
            record.updatedAt = clock.instant();
            persist();
            notifyListeners();
            return;
        }
        record.status = DownloadStatus.COMPLETED;
        record.error = null;
        record.updatedAt = clock.instant();
        persist();
        notifyListeners();
    }

    private void finalizeVideo(DownloadRecord record) {
        VideoFinalizationRequest request;
        synchronized (this) {
            if (!videoFinalizer.available()
                    || !readyToFinalize(record)) {
                return;
            }
            request = new VideoFinalizationRequest(
                    record.video.hls() ? playlistFile(record) : videoFile(record),
                    finalizedVideoFile(record));
        }
        videoFinalizer.finalizeVideo(request, () -> finalizationCancelled(record));
        synchronized (this) {
            if (!readyToFinalize(record)) {
                deleteFinalizedVideo(request.output());
                return;
            }
            long finalizedBytes = finalizedVideoBytes(request.output());
            long retainedStorage = Math.max(0L, usedStorageBytes - record.downloadedBytes);
            if (retainedStorage > policy.maximumStorageBytes() - finalizedBytes) {
                deleteFinalizedVideo(request.output());
                throw new DownloadException("Finalized video exceeds the configured storage limit");
            }
            deleteVideoIntermediates(record);
            usedStorageBytes = retainedStorage + finalizedBytes;
            record.downloadedBytes = finalizedBytes;
            record.status = DownloadStatus.COMPLETED;
            record.error = null;
            record.updatedAt = clock.instant();
            persist();
            notifyListeners();
        }
    }

    private synchronized boolean finalizationCancelled(DownloadRecord record) {
        return !readyToFinalize(record);
    }

    private boolean readyToFinalize(DownloadRecord record) {
        return !closed
                && !offlineMode
                && records.get(record.id) == record
                && record.status == DownloadStatus.DOWNLOADING
                && record.completedPages == record.pages.size();
    }

    private static long finalizedVideoBytes(Path output) {
        try {
            if (!Files.isRegularFile(output)) {
                throw new DownloadException("Video finalization did not produce a media file");
            }
            long bytes = Files.size(output);
            if (bytes < 1L) {
                throw new DownloadException("Video finalization produced an empty media file");
            }
            return bytes;
        } catch (IOException exception) {
            throw new DownloadException("Unable to inspect finalized video", exception);
        }
    }

    private static void deleteFinalizedVideo(Path output) {
        try {
            Files.deleteIfExists(output);
        } catch (IOException exception) {
            throw new DownloadException("Unable to remove incomplete finalized video", exception);
        }
    }

    private void publishProgress(DownloadRecord record) {
        long now = System.nanoTime();
        if (now - record.lastProgressNotificationNanos >= PROGRESS_NOTIFICATION_INTERVAL_NANOS) {
            record.lastProgressNotificationNanos = now;
            notifyListeners();
        }
    }

    private void schedule(DownloadRecord record) {
        if (record.status == DownloadStatus.QUEUED) {
            scheduleAvailable();
        }
    }

    private void scheduleAvailable() {
        while (!closed && !offlineMode && largeTransfersAllowed.getAsBoolean()
                && scheduled.size() < policy.concurrentJobs()) {
            Optional<DownloadRecord> next = records.values().stream()
                    .filter(record -> record.status == DownloadStatus.QUEUED)
                    .filter(record -> !scheduled.contains(record.id))
                    .sorted(QUEUE_ORDER)
                    .findFirst();
            if (next.isEmpty()) {
                return;
            }
            DownloadRecord record = next.orElseThrow();
            scheduled.add(record.id);
            workers.execute(() -> run(record.id));
        }
    }

    private void transition(DownloadRecord record, DownloadStatus status, String error) {
        record.status = status;
        record.error = error;
        if (status != DownloadStatus.DOWNLOADING) {
            record.bytesPerSecond = 0L;
        }
        record.updatedAt = clock.instant();
        persist();
        notifyListeners();
    }

    private void fail(DownloadRecord record, RuntimeException exception) {
        String message = exception.getMessage();
        transition(
                record,
                DownloadStatus.FAILED,
                message == null || message.isBlank() ? exception.getClass().getSimpleName() : message);
    }

    private PagedSource pagedSource(SourceId sourceId) {
        Source source = sources.find(sourceId)
                .orElseThrow(() -> new DownloadException("Download source is not installed"));
        if (!(source instanceof PagedSource pagedSource)) {
            throw new DownloadException("Download source does not provide paged content");
        }
        return pagedSource;
    }

    private StreamingSource streamingSource(SourceId sourceId) {
        Source source = sources.find(sourceId)
                .orElseThrow(() -> new DownloadException("Download source is not installed"));
        if (!(source instanceof StreamingSource streamingSource)) {
            throw new DownloadException("Download source does not provide episodes");
        }
        return streamingSource;
    }

    private static List<SourceEpisode> validatedEpisodes(
            StreamingSource source,
            SourceCatalogueItemId itemId) {
        List<SourceEpisode> episodes = List.copyOf(Objects.requireNonNull(
                source.episodes(itemId),
                "streaming source returned null episodes"));
        if (episodes.isEmpty() || episodes.stream().anyMatch(episode -> !episode.id().itemId().equals(itemId))) {
            throw new DownloadException("Download source returned invalid episodes");
        }
        return episodes;
    }

    private static List<SourceVideoStream> validatedStreams(
            StreamingSource source,
            SourceEpisodeId episodeId) {
        List<SourceVideoStream> streams = List.copyOf(Objects.requireNonNull(
                source.streams(episodeId),
                "streaming source returned null streams"));
        if (streams.isEmpty()) {
            throw new DownloadException("Episode contains no downloadable video stream");
        }
        Set<String> identities = new HashSet<>();
        if (streams.stream().anyMatch(stream -> !identities.add(stream.id()))) {
            throw new DownloadException("Download source returned duplicate video streams");
        }
        return streams;
    }

    private static SourceEpisode selectEpisode(LibraryItem item, List<SourceEpisode> episodes) {
        Optional<String> preferred = item.progress().map(progress -> progress.contentId());
        if (preferred.isPresent()) {
            Optional<SourceEpisode> match = episodes.stream()
                    .filter(episode -> episode.id().value().equals(preferred.orElseThrow()))
                    .findFirst();
            if (match.isPresent()) {
                return match.orElseThrow();
            }
        }
        return episodes.getLast();
    }

    private static List<SourceContentUnit> validatedUnits(
            PagedSource source,
            SourceCatalogueItemId itemId) {
        List<SourceContentUnit> units = List.copyOf(Objects.requireNonNull(
                source.contentUnits(itemId),
                "paged source returned null content units"));
        if (units.isEmpty() || units.stream().anyMatch(unit -> !unit.id().itemId().equals(itemId))) {
            throw new DownloadException("Download source returned invalid content units");
        }
        return units;
    }

    private static SourceContentUnit selectUnit(LibraryItem item, List<SourceContentUnit> units) {
        Optional<String> preferred = item.progress().map(progress -> progress.contentId());
        if (preferred.isPresent()) {
            Optional<SourceContentUnit> match = units.stream()
                    .filter(unit -> unit.id().value().equals(preferred.orElseThrow()))
                    .findFirst();
            if (match.isPresent()) {
                return match.orElseThrow();
            }
        }
        return units.getLast();
    }

    private static List<SourcePageResource> validatedPages(
            PagedSource source,
            SourceContentUnit unit) {
        List<SourcePageResource> pages = new ArrayList<>(Objects.requireNonNull(
                source.pages(unit.id()),
                "paged source returned null pages"));
        pages.sort(Comparator.comparingInt(SourcePageResource::index));
        if (pages.isEmpty()) {
            throw new DownloadException("Content unit contains no downloadable pages");
        }
        for (int index = 0; index < pages.size(); index++) {
            SourcePageResource page = pages.get(index);
            if (!page.contentUnitId().equals(unit.id()) || page.index() != index) {
                throw new DownloadException("Download source returned an invalid page sequence");
            }
        }
        return List.copyOf(pages);
    }

    private void load() {
        try {
            long maximumStorageBytes = storagePolicyStore.load().orElse(policy.maximumStorageBytes());
            try {
                policy = policy.withMaximumStorageBytes(maximumStorageBytes);
            } catch (IllegalArgumentException exception) {
                throw new IOException("Invalid persisted download storage limit", exception);
            }
            automaticPolicy = automaticPolicyStore.load();
            storageRoot = locationStore.load().orElse(defaultStorageRoot);
            storageRoot = validateStorageRoot(storageRoot);
            contentRoot = storageRoot.resolve("content").normalize();
            Files.createDirectories(contentRoot);
            FileDownloadQueueStore.LoadResult loaded = store.load();
            offlineMode = loaded.offlineMode();
            for (DownloadRecord record : loaded.records()) {
                if (records.put(record.id, record) != null) {
                    throw new DownloadException("Downloads queue contains duplicate job ids");
                }
                reconcile(record);
                nextQueueOrder = Math.max(nextQueueOrder, record.queueOrder + 1L);
            }
            persist();
            if (!offlineMode) {
                scheduleAvailable();
            }
        } catch (IOException exception) {
            throw new DownloadException("Unable to load downloads queue", exception);
        }
    }

    private void reconcile(DownloadRecord record) throws IOException {
        if (record.queueOrder < 0) {
            throw new IOException("Download queue position must not be negative");
        }
        if (record.video()
                && record.status != DownloadStatus.CANCELLED
                && Files.isRegularFile(finalizedVideoFile(record))
                && Files.size(finalizedVideoFile(record)) > 0L) {
            reconcileFinalizedVideo(record);
            return;
        }
        if (record.video() && Files.isRegularFile(finalizedVideoFile(record))) {
            Files.deleteIfExists(finalizedVideoFile(record));
        }
        if (record.video() && !record.video.hls()) {
            reconcileProgressiveVideo(record);
            return;
        }
        int completePages = 0;
        long bytes = 0;
        while (completePages < record.pages.size()) {
            Path page = pageFile(record, completePages);
            if (!Files.isRegularFile(page)) {
                break;
            }
            bytes += Files.size(page);
            completePages++;
        }
        record.completedPages = completePages;
        record.downloadedBytes = bytes;
        usedStorageBytes += bytes;
        if (record.status == DownloadStatus.DOWNLOADING || record.status == DownloadStatus.QUEUED) {
            record.status = offlineMode || !policy.resumeOnStart()
                    ? DownloadStatus.PAUSED
                    : DownloadStatus.QUEUED;
        }
        if (record.status == DownloadStatus.COMPLETED && completePages != record.pages.size()) {
            record.status = offlineMode || !policy.resumeOnStart()
                    ? DownloadStatus.PAUSED
                    : DownloadStatus.QUEUED;
            record.error = "Downloaded page set was incomplete and will be resumed";
        }
        if (record.video() && record.status == DownloadStatus.COMPLETED && !Files.isRegularFile(playlistFile(record))) {
            writeOfflinePlaylist(record);
        }
    }

    private void reconcileFinalizedVideo(DownloadRecord record) throws IOException {
        Path finalized = finalizedVideoFile(record);
        long bytes = Files.size(finalized);
        deleteVideoIntermediates(record);
        record.completedPages = record.pages.size();
        record.downloadedBytes = bytes;
        record.status = DownloadStatus.COMPLETED;
        record.error = null;
        record.bytesPerSecond = 0L;
        usedStorageBytes += bytes;
    }

    private void reconcileProgressiveVideo(DownloadRecord record) throws IOException {
        Path file = videoFile(record);
        long available = Files.isRegularFile(file) ? Files.size(file) : 0L;
        int complete = 0;
        long expected = 0L;
        while (complete < record.pages.size()) {
            long size = record.pages.get(complete).estimatedBytes();
            if (size < 0L || expected + size > available) {
                break;
            }
            expected += size;
            complete++;
        }
        if (available != expected && Files.isRegularFile(file)) {
            try (var channel = FileChannel.open(
                    file,
                    StandardOpenOption.WRITE)) {
                channel.truncate(expected);
            }
        }
        record.completedPages = complete;
        record.downloadedBytes = expected;
        usedStorageBytes += expected;
        if (record.status == DownloadStatus.DOWNLOADING || record.status == DownloadStatus.QUEUED) {
            record.status = offlineMode || !policy.resumeOnStart()
                    ? DownloadStatus.PAUSED
                    : DownloadStatus.QUEUED;
        }
        if (record.status == DownloadStatus.COMPLETED && complete != record.pages.size()) {
            record.status = offlineMode || !policy.resumeOnStart()
                    ? DownloadStatus.PAUSED
                    : DownloadStatus.QUEUED;
            record.error = "Downloaded video was incomplete and will be resumed";
        }
    }

    private void writePage(DownloadRecord record, int index, byte[] bytes) {
        Path target = pageFile(record, index);
        Path temporary = target.resolveSibling(target.getFileName() + ".tmp");
        try {
            Files.createDirectories(target.getParent());
            Files.write(temporary, bytes);
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            throw new DownloadException("Unable to store downloaded page", exception);
        }
    }

    private void appendVideoChunk(
            DownloadRecord record,
            VideoDownloadResource resource,
            byte[] bytes) {
        Path target = videoFile(record);
        try {
            Files.createDirectories(target.getParent());
            long existing = Files.exists(target) ? Files.size(target) : 0L;
            if (existing != resource.rangeStart()) {
                throw new DownloadException("Downloaded video chunk order is inconsistent");
            }
            Files.write(
                    target,
                    bytes,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND);
        } catch (IOException exception) {
            throw new DownloadException("Unable to store downloaded video", exception);
        }
    }

    private void writeOfflinePlaylist(DownloadRecord record) {
        Path target = playlistFile(record);
        Path temporary = target.resolveSibling(target.getFileName() + ".tmp");
        try {
            Files.createDirectories(target.getParent());
            Files.writeString(temporary, record.video.offlinePlaylist(), StandardCharsets.UTF_8);
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            throw new DownloadException("Unable to store the offline video playlist", exception);
        }
    }

    private void deleteFiles(DownloadRecord record) {
        deleteDirectory(recordDirectory(record), true);
    }

    private void removeRecordSafely(DownloadRecord record) {
        records.remove(record.id);
        deferredDeletions.remove(record.id);
        if (scheduled.contains(record.id)) {
            deferredRemovals.put(record.id, record);
        } else {
            deleteFiles(record);
        }
    }

    private void completeDeferredCleanup(DownloadId id) {
        DownloadRecord removed = deferredRemovals.remove(id);
        if (removed != null) {
            try {
                deleteFiles(removed);
            } catch (RuntimeException exception) {
                removed.error = "Unable to remove downloaded files: " + message(exception);
                removed.updatedAt = clock.instant();
                records.put(removed.id, removed);
                persist();
                notifyListeners();
            }
            return;
        }
        if (!deferredDeletions.remove(id)) {
            return;
        }
        DownloadRecord record = records.get(id);
        if (record == null) {
            return;
        }
        try {
            deleteFiles(record);
            resetDownloadedProgress(record);
        } catch (RuntimeException exception) {
            record.error = "Unable to remove partial download: " + message(exception);
            record.updatedAt = clock.instant();
        }
        persist();
        notifyListeners();
    }

    private static void resetDownloadedProgress(DownloadRecord record) {
        record.completedPages = 0;
        record.downloadedBytes = 0L;
        record.bytesPerSecond = 0L;
    }

    private void deleteDirectory(Path directory, boolean accountStorage) {
        if (!Files.exists(directory)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(directory)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                if (accountStorage && Files.isRegularFile(path)
                        && (path.getFileName().toString().endsWith(".page")
                        || path.getFileName().toString().endsWith(".media")
                        || path.getFileName().toString().endsWith(".mkv"))) {
                    usedStorageBytes -= Files.size(path);
                }
                Files.deleteIfExists(path);
            }
            usedStorageBytes = Math.max(0, usedStorageBytes);
        } catch (IOException exception) {
            throw new DownloadException("Unable to remove downloaded pages", exception);
        }
    }

    private Path pageFile(DownloadRecord record, int index) {
        return recordDirectory(record).resolve(pageFileName(index));
    }

    private Path videoFile(DownloadRecord record) {
        return recordDirectory(record).resolve("offline.media");
    }

    private Path playlistFile(DownloadRecord record) {
        return recordDirectory(record).resolve("offline.m3u8");
    }

    private Path finalizedVideoFile(DownloadRecord record) {
        return recordDirectory(record).resolve("offline.mkv");
    }

    private void deleteVideoIntermediates(DownloadRecord record) {
        try {
            for (int index = 0; index < record.pages.size(); index++) {
                Files.deleteIfExists(pageFile(record, index));
            }
            Files.deleteIfExists(videoFile(record));
            Files.deleteIfExists(playlistFile(record));
        } catch (IOException exception) {
            throw new DownloadException("Unable to remove finalized video fragments", exception);
        }
    }

    private Path recordDirectory(DownloadRecord record) {
        return recordDirectory(record, contentRoot);
    }

    private static Path recordDirectory(DownloadRecord record, Path root) {
        Path directory = root.resolve(record.id.toString()).normalize();
        if (!directory.getParent().equals(root)) {
            throw new DownloadException("Invalid download storage path");
        }
        return directory;
    }

    private DownloadRecord record(DownloadId id) {
        Objects.requireNonNull(id, "id must not be null");
        ensureOpen();
        DownloadRecord record = records.get(id);
        if (record == null) {
            throw new DownloadException("Download job was not found");
        }
        return record;
    }

    private void ensureOnline() {
        if (offlineMode) {
            throw new DownloadException("Downloads cannot resume while offline mode is enabled");
        }
        ensureLargeTransfersAllowed();
    }

    private void ensureLargeTransfersAllowed() {
        if (!largeTransfersAllowed.getAsBoolean()) {
            throw new DownloadException("Downloads are waiting for an allowed network connection");
        }
    }

    private void persist() {
        try {
            store.save(offlineMode, records.values());
        } catch (IOException exception) {
            throw new DownloadException("Unable to persist downloads queue", exception);
        }
    }

    private void notifyListeners() {
        List.copyOf(listeners).forEach(listener -> {
            try {
                listener.run();
            } catch (RuntimeException ignored) {
                // Observers cannot compromise queue durability or worker progress.
            }
        });
    }

    private synchronized void removeListener(Runnable listener) {
        listeners.remove(listener);
    }

    private void ensureOpen() {
        if (closed) {
            throw new DownloadException("Download service is closed");
        }
    }

    private Path validateStorageRoot(Path location) {
        Path normalized = Objects.requireNonNull(location, "location must not be null")
                .toAbsolutePath()
                .normalize();
        Path probe = null;
        try {
            if (Files.exists(normalized) && !Files.isDirectory(normalized)) {
                throw new DownloadException("Download storage location must be a directory");
            }
            Path managedContent = normalized.resolve("content").normalize();
            if (!managedContent.getParent().equals(normalized)) {
                throw new DownloadException("Invalid download storage location");
            }
            Files.createDirectories(managedContent);
            probe = Files.createTempFile(managedContent, ".anilib-write-", ".tmp");
            return normalized;
        } catch (IOException exception) {
            throw new DownloadException("Download storage location is not writable", exception);
        } finally {
            if (probe != null) {
                try {
                    Files.deleteIfExists(probe);
                } catch (IOException ignored) {
                    // The storage validation result remains actionable.
                }
            }
        }
    }

    private void copyManagedContent(Path sourceRoot, Path targetRoot) throws IOException {
        Files.createDirectories(targetRoot);
        for (DownloadRecord record : records.values()) {
            Path sourceDirectory = recordDirectory(record, sourceRoot);
            Path targetDirectory = recordDirectory(record, targetRoot);
            if (Files.exists(targetDirectory)) {
                deleteDirectory(targetDirectory, false);
            }
            if (!Files.isDirectory(sourceDirectory)) {
                continue;
            }
            try (Stream<Path> files = Files.list(sourceDirectory)) {
                for (Path source : files.filter(Files::isRegularFile)
                        .filter(DefaultDownloadService::isManagedContentFile)
                        .toList()) {
                Files.createDirectories(targetDirectory);
                Path target = targetDirectory.resolve(source.getFileName().toString());
                Path temporary = target.resolveSibling(target.getFileName() + ".tmp");
                Files.copy(source, temporary, StandardCopyOption.REPLACE_EXISTING);
                if (Files.size(source) != Files.size(temporary)) {
                    throw new IOException("Migrated download content size did not match");
                }
                try {
                    Files.move(
                            temporary,
                            target,
                            StandardCopyOption.ATOMIC_MOVE,
                            StandardCopyOption.REPLACE_EXISTING);
                } catch (AtomicMoveNotSupportedException exception) {
                    Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
                }
            }
            }
        }
    }

    private static boolean isManagedContentFile(Path path) {
        String name = path.getFileName().toString();
        return name.endsWith(".page")
                || name.equals("offline.media")
                || name.equals("offline.m3u8")
                || name.equals("offline.mkv");
    }

    private void restoreMigratedStatuses(Map<DownloadId, DownloadStatus> previousStatuses) {
        previousStatuses.forEach((id, status) -> {
            DownloadRecord record = records.get(id);
            if (record != null) {
                record.status = status == DownloadStatus.DOWNLOADING ? DownloadStatus.QUEUED : status;
                record.updatedAt = clock.instant();
            }
        });
    }

    private void cleanupMigratedContent(Path previousContent) {
        for (DownloadRecord record : records.values()) {
            Path directory = recordDirectory(record, previousContent);
            try {
                deleteDirectory(directory, false);
            } catch (DownloadException ignored) {
                // The committed target remains authoritative if old storage cleanup fails.
            }
        }
    }

    private static boolean isDownloadDirectory(Path entry) {
        try {
            DownloadId.parse(entry.getFileName().toString());
            return true;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private void finishBulkChange(boolean changed) {
        if (changed) {
            persist();
            notifyListeners();
        }
    }

    private static String pageFileName(int index) {
        return String.format(Locale.ROOT, "%08d.page", index);
    }

    private int automaticLimit(LibraryItem item) {
        if (item.categories().isEmpty()) {
            return automaticPolicy.includeUncategorized() ? defaultAutomaticLimit(item.kind()) : 0;
        }
        return automaticPolicy.categoryRules().stream()
                .filter(rule -> item.categories().contains(rule.category()))
                .mapToInt(rule -> automaticLimit(item.kind(), rule))
                .max()
                .orElse(0);
    }

    private int defaultAutomaticLimit(MediaKind kind) {
        return kind == MediaKind.ANIME
                ? automaticPolicy.defaultEpisodeLimit()
                : automaticPolicy.defaultChapterLimit();
    }

    private static int automaticLimit(MediaKind kind, AutomaticDownloadCategoryRule rule) {
        return kind == MediaKind.ANIME ? rule.episodeLimit() : rule.chapterLimit();
    }

    private boolean hasDownload(SourceContentUnitId id) {
        return records.values().stream()
                .anyMatch(record -> record.contentUnit.id().equals(id)
                        && record.status != DownloadStatus.CANCELLED);
    }

    private void removeRecords(Collection<DownloadRecord> removable) {
        if (removable.isEmpty()) {
            return;
        }
        removable.forEach(record -> record.status = DownloadStatus.CANCELLED);
        removable.forEach(this::deleteFiles);
        removable.forEach(record -> records.remove(record.id));
        persist();
        notifyListeners();
    }

    private void persistAutomaticPolicy() {
        try {
            automaticPolicyStore.save(automaticPolicy);
        } catch (IOException exception) {
            throw new DownloadException("Unable to persist automatic download policy", exception);
        }
    }

    private static String message(RuntimeException exception) {
        String value = exception.getMessage();
        return value == null || value.isBlank() ? exception.getClass().getSimpleName() : value;
    }

    private static long saturatedRate(long bytes, long elapsedNanos) {
        if (bytes <= 0L) {
            return 0L;
        }
        long scaled = bytes > Long.MAX_VALUE / 1_000_000_000L
                ? Long.MAX_VALUE
                : bytes * 1_000_000_000L;
        return Math.max(1L, scaled / elapsedNanos);
    }

    private static AnilibHttpClient unavailableHttpClient() {
        return request -> {
            throw new HttpException("Video download networking is not configured");
        };
    }

    @Override
    public void close() {
        synchronized (this) {
            if (closed) {
                return;
            }
            for (DownloadRecord record : records.values()) {
                if (record.status == DownloadStatus.DOWNLOADING) {
                    record.status = policy.resumeOnStart() ? DownloadStatus.QUEUED : DownloadStatus.PAUSED;
                    record.updatedAt = clock.instant();
                }
            }
            persist();
            closed = true;
            workers.shutdownNow();
            listeners.clear();
        }
        try {
            if (!workers.awaitTermination(5, TimeUnit.SECONDS)) {
                throw new DownloadException("Download workers did not stop within five seconds");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new DownloadException("Interrupted while stopping download workers", exception);
        }
    }
}
