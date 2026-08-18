package fr.vriege.anilib.feature.downloads.runtime;

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
import fr.vriege.anilib.feature.library.LibraryCatalog;
import fr.vriege.anilib.feature.library.LibraryItem;
import fr.vriege.anilib.feature.library.LibraryItemId;
import fr.vriege.anilib.feature.library.LibraryOrigin;
import fr.vriege.anilib.feature.library.MediaKind;
import fr.vriege.anilib.feature.reader.ReaderContent;
import fr.vriege.anilib.feature.reader.ReaderContentProvider;
import fr.vriege.anilib.feature.source.PagedSource;
import fr.vriege.anilib.feature.source.Source;
import fr.vriege.anilib.feature.source.SourceCatalogueItemId;
import fr.vriege.anilib.feature.source.SourceContentUnit;
import fr.vriege.anilib.feature.source.SourceContentUnitId;
import fr.vriege.anilib.feature.source.SourceId;
import fr.vriege.anilib.feature.source.SourcePageResource;
import fr.vriege.anilib.feature.source.SourceRegistry;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
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
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

public final class DefaultDownloadService
        implements DownloadService, ReaderContentProvider, AutoCloseable {
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
    private final DownloadStoragePolicy policy;
    private final Clock clock;
    private final FileDownloadQueueStore store;
    private final FileDownloadStorageLocationStore locationStore;
    private final FileAutomaticDownloadPolicyStore automaticPolicyStore;
    private final ExecutorService workers;
    private final BooleanSupplier largeTransfersAllowed;
    private final Map<DownloadId, DownloadRecord> records = new LinkedHashMap<>();
    private final Set<DownloadId> scheduled = new HashSet<>();
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
        this(sources, library, root, policy, () -> true);
    }

    public DefaultDownloadService(
            SourceRegistry sources,
            LibraryCatalog library,
            Path root,
            DownloadStoragePolicy policy,
            BooleanSupplier largeTransfersAllowed) {
        this(
                sources,
                library,
                root,
                policy,
                Clock.systemUTC(),
                Executors.newFixedThreadPool(policy.concurrentJobs()),
                largeTransfersAllowed);
    }

    DefaultDownloadService(
            SourceRegistry sources,
            LibraryCatalog library,
            Path root,
            DownloadStoragePolicy policy,
            Clock clock,
            ExecutorService workers,
            BooleanSupplier largeTransfersAllowed) {
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
        this.store = new FileDownloadQueueStore(normalizedRoot.resolve("queue.anilib"));
        this.locationStore = new FileDownloadStorageLocationStore(normalizedRoot.resolve("storage-location.anilib"));
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
        return library.find(libraryItemId)
                .flatMap(LibraryItem::origin)
                .flatMap(origin -> sources.find(SourceId.of(origin.sourceId())))
                .filter(PagedSource.class::isInstance)
                .isPresent();
    }

    @Override
    public synchronized DownloadId enqueue(LibraryItemId libraryItemId) {
        return enqueue(libraryItemId, (SourceContentUnitId) null);
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
        LibraryOrigin origin = library.find(libraryItemId)
                .flatMap(LibraryItem::origin)
                .orElseThrow(() -> new DownloadException("Library item has no source origin"));
        SourceCatalogueItemId itemId = new SourceCatalogueItemId(
                SourceId.of(origin.sourceId()),
                origin.sourceItemKey());
        return enqueue(libraryItemId, new SourceContentUnitId(itemId, sourceContentId));
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
                deleteFiles(record);
                record.completedPages = 0;
                record.downloadedBytes = 0;
            }
            persist();
            notifyListeners();
        }
    }

    @Override
    public synchronized void remove(DownloadId id) {
        DownloadRecord record = record(id);
        record.status = DownloadStatus.CANCELLED;
        deleteFiles(record);
        records.remove(record.id);
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
        removed.forEach(this::deleteFiles);
        records.clear();
        scheduled.clear();
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
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
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
                    java.util.stream.Collectors.toSet());
            try (java.util.stream.Stream<Path> entries = Files.list(contentRoot)) {
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
                PagedSource source = pagedSource(itemId.sourceId());
                List<SourceContentUnit> units = validatedUnits(source, itemId);
                int first = Math.max(0, units.size() - limit);
                for (SourceContentUnit unit : units.subList(first, units.size())) {
                    if (hasDownload(unit.id())) {
                        continue;
                    }
                    try {
                        enqueue(item.id(), unit.id());
                        enqueued++;
                    } catch (DownloadException exception) {
                        failures.add(item.title() + " · " + unit.title() + ": " + message(exception));
                    }
                }
            } catch (DownloadException exception) {
                failures.add(item.title() + ": " + message(exception));
            }
        }
        return new AutomaticDownloadResult(enqueued, removed, failures);
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
        removed.forEach(this::deleteFiles);
        removed.forEach(record -> records.remove(record.id));
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
            deleteFiles(record);
            records.remove(record.id);
            scheduled.remove(record.id);
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
            PagedSource source = pagedSource(record.sourceItemId.sourceId());
            while (downloadNextPage(record, source)) {
                // Continue until paused, failed, cancelled, or complete.
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
                scheduleAvailable();
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
            persist();
            notifyListeners();
            return record.status == DownloadStatus.DOWNLOADING;
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

    private void deleteFiles(DownloadRecord record) {
        deleteDirectory(recordDirectory(record), true);
    }

    private void deleteDirectory(Path directory, boolean accountStorage) {
        if (!Files.exists(directory)) {
            return;
        }
        try (java.util.stream.Stream<Path> paths = Files.walk(directory)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                if (accountStorage && Files.isRegularFile(path)
                        && path.getFileName().toString().endsWith(".page")) {
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
            for (int index = 0; index < record.pages.size(); index++) {
                Path source = sourceDirectory.resolve(pageFileName(index));
                if (!Files.isRegularFile(source)) {
                    continue;
                }
                Files.createDirectories(targetDirectory);
                Path target = targetDirectory.resolve(pageFileName(index));
                Path temporary = target.resolveSibling(target.getFileName() + ".tmp");
                Files.copy(source, temporary, StandardCopyOption.REPLACE_EXISTING);
                if (Files.size(source) != Files.size(temporary)) {
                    throw new IOException("Migrated download page size did not match");
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
        return String.format(java.util.Locale.ROOT, "%08d.page", index);
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
