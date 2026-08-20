package fr.vriege.anilib.feature.updates.runtime;

import fr.vriege.anilib.feature.library.LibraryCatalog;
import fr.vriege.anilib.feature.library.LibraryCategory;
import fr.vriege.anilib.feature.library.LibraryCategoryUpdatePolicy;
import fr.vriege.anilib.feature.library.LibraryConfigurationSnapshot;
import fr.vriege.anilib.feature.library.LibraryItem;
import fr.vriege.anilib.feature.library.LibraryItemId;
import fr.vriege.anilib.feature.library.LibraryOrigin;
import fr.vriege.anilib.feature.library.MediaKind;
import fr.vriege.anilib.feature.library.PublicationStatus;
import fr.vriege.anilib.feature.source.PagedSource;
import fr.vriege.anilib.feature.source.Source;
import fr.vriege.anilib.feature.source.SourceCatalogueItemId;
import fr.vriege.anilib.feature.source.SourceContentUnit;
import fr.vriege.anilib.feature.source.SourceEpisode;
import fr.vriege.anilib.feature.source.SourceId;
import fr.vriege.anilib.feature.source.SourceRegistry;
import fr.vriege.anilib.feature.source.StreamingSource;
import fr.vriege.anilib.feature.updates.LibraryUpdateEvent;
import fr.vriege.anilib.feature.updates.LibraryUpdateEventId;
import fr.vriege.anilib.feature.updates.LibraryUpdateException;
import fr.vriege.anilib.feature.updates.LibraryUpdateFailure;
import fr.vriege.anilib.feature.updates.LibraryUpdateNotification;
import fr.vriege.anilib.feature.updates.LibraryUpdateNotificationType;
import fr.vriege.anilib.feature.updates.LibraryUpdateNotifier;
import fr.vriege.anilib.feature.updates.LibraryUpdatePolicy;
import fr.vriege.anilib.feature.updates.LibraryUpdateService;
import fr.vriege.anilib.feature.updates.LibraryUpdateSkip;
import fr.vriege.anilib.feature.updates.LibraryUpdateSkipReason;
import fr.vriege.anilib.feature.updates.LibraryUpdateSnapshot;
import fr.vriege.anilib.feature.updates.LibraryUpdateStatus;
import fr.vriege.anilib.feature.updates.UpdateInterval;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import java.util.Collections;
import java.util.stream.Collectors;

public final class DefaultLibraryUpdateService implements LibraryUpdateService, AutoCloseable {
    private static final int SOURCE_LANES = 5;
    private static final int MAXIMUM_VISIBLE_EVENTS = 100_000;
    private static final Comparator<LibraryUpdateEvent> EVENT_ORDER = Comparator
            .comparing(LibraryUpdateEvent::discoveredAt)
            .reversed()
            .thenComparing(event -> event.libraryItemId().value())
            .thenComparing(LibraryUpdateEvent::sourceContentId);
    private final LibraryCatalog library;
    private final SourceRegistry sources;
    private final LibraryUpdateNotifier notifier;
    private final LibraryUpdateStore store;
    private final Clock clock;
    private final ExecutorService coordinator;
    private final ExecutorService workers;
    private final ScheduledExecutorService scheduler;
    private final BooleanSupplier largeTransfersAllowed;
    private final Supplier<LibraryConfigurationSnapshot> libraryConfiguration;
    private final CopyOnWriteArrayList<Runnable> listeners = new CopyOnWriteArrayList<>();
    private final AtomicBoolean cancellation = new AtomicBoolean();
    private volatile LibraryUpdateStatus status = LibraryUpdateStatus.IDLE;
    private volatile int completedTitles;
    private volatile int totalTitles;
    private volatile List<String> activeTitles = List.of();
    private volatile List<LibraryUpdateEvent> discovered = List.of();
    private volatile List<LibraryUpdateFailure> failures = List.of();
    private volatile Optional<Instant> nextRunAt = Optional.empty();
    private CompletableFuture<LibraryUpdateSnapshot> running;
    private ScheduledFuture<?> scheduled;
    private boolean closed;

    public DefaultLibraryUpdateService(
            LibraryCatalog library,
            SourceRegistry sources,
            LibraryUpdateNotifier notifier,
            Path stateFile) {
        this(
                library,
                sources,
                notifier,
                stateFile,
                Clock.systemUTC(),
                LibraryConfigurationSnapshot::defaults,
                () -> true);
    }

    public DefaultLibraryUpdateService(
            LibraryCatalog library,
            SourceRegistry sources,
            LibraryUpdateNotifier notifier,
            Path stateFile,
            BooleanSupplier largeTransfersAllowed) {
        this(
                library,
                sources,
                notifier,
                stateFile,
                Clock.systemUTC(),
                LibraryConfigurationSnapshot::defaults,
                largeTransfersAllowed);
    }

    public DefaultLibraryUpdateService(
            LibraryCatalog library,
            SourceRegistry sources,
            LibraryUpdateNotifier notifier,
            Path stateFile,
            Supplier<LibraryConfigurationSnapshot> libraryConfiguration,
            BooleanSupplier largeTransfersAllowed) {
        this(
                library,
                sources,
                notifier,
                stateFile,
                Clock.systemUTC(),
                libraryConfiguration,
                largeTransfersAllowed);
    }

    public DefaultLibraryUpdateService(
            LibraryCatalog library,
            SourceRegistry sources,
            LibraryUpdateNotifier notifier,
            Path stateFile,
            Clock clock,
            BooleanSupplier largeTransfersAllowed) {
        this(
                library,
                sources,
                notifier,
                stateFile,
                clock,
                LibraryConfigurationSnapshot::defaults,
                largeTransfersAllowed);
    }

    public DefaultLibraryUpdateService(
            LibraryCatalog library,
            SourceRegistry sources,
            LibraryUpdateNotifier notifier,
            Path stateFile,
            Clock clock,
            Supplier<LibraryConfigurationSnapshot> libraryConfiguration,
            BooleanSupplier largeTransfersAllowed) {
        this.library = Objects.requireNonNull(library, "library must not be null");
        this.sources = Objects.requireNonNull(sources, "sources must not be null");
        this.notifier = Objects.requireNonNull(notifier, "notifier must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.largeTransfersAllowed = Objects.requireNonNull(
                largeTransfersAllowed,
                "largeTransfersAllowed must not be null");
        this.libraryConfiguration = Objects.requireNonNull(
                libraryConfiguration,
                "libraryConfiguration must not be null");
        store = new LibraryUpdateStore(stateFile);
        coordinator = Executors.newSingleThreadExecutor(task -> daemon(task, "anilib-library-update"));
        workers = Executors.newFixedThreadPool(
                SOURCE_LANES,
                task -> daemon(task, "anilib-library-update-source"));
        scheduler = Executors.newSingleThreadScheduledExecutor(
                task -> daemon(task, "anilib-library-update-scheduler"));
        scheduleNext();
    }

    @Override
    public LibraryUpdateSnapshot snapshot() {
        LibraryUpdateStore.State durable = store.snapshot();
        Map<EventKey, LibraryUpdateEvent> visible = new LinkedHashMap<>();
        discovered.forEach(event -> visible.put(EventKey.of(event), event));
        durable.events().forEach(event -> visible.putIfAbsent(EventKey.of(event), event));
        return new LibraryUpdateSnapshot(
                durable.policy(),
                status,
                completedTitles,
                totalTitles,
                activeTitles,
                visible.values().stream().sorted(EVENT_ORDER).toList(),
                failures,
                skippedTitles(durable.policy()),
                durable.lastRunAt(),
                nextRunAt);
    }

    @Override
    public synchronized CompletableFuture<LibraryUpdateSnapshot> runNow() {
        ensureOpen();
        if (!largeTransfersAllowed.getAsBoolean()) {
            return CompletableFuture.failedFuture(new LibraryUpdateException(
                    "Library updates are waiting for an allowed network connection"));
        }
        if (running != null && !running.isDone()) {
            return running;
        }
        cancellation.set(false);
        completedTitles = 0;
        totalTitles = 0;
        activeTitles = List.of();
        discovered = List.of();
        failures = List.of();
        status = LibraryUpdateStatus.RUNNING;
        notifyListeners();
        running = CompletableFuture.supplyAsync(this::execute, coordinator);
        return running;
    }

    @Override
    public synchronized boolean cancel() {
        if (running == null || running.isDone()) {
            return false;
        }
        cancellation.set(true);
        return true;
    }

    @Override
    public synchronized void configure(LibraryUpdatePolicy policy) {
        ensureOpen();
        LibraryUpdateStore.State current = store.snapshot();
        store.replace(new LibraryUpdateStore.State(
                Objects.requireNonNull(policy, "policy must not be null"),
                current.baselines(),
                current.events(),
                current.lastRunAt()));
        scheduleNext();
        notifyListeners();
    }

    @Override
    public synchronized void markAllRead() {
        setEventsRead(snapshot().events().stream()
                .map(LibraryUpdateEvent::id)
                .collect(Collectors.toUnmodifiableSet()), true);
    }

    @Override
    public synchronized void setEventsRead(Set<LibraryUpdateEventId> ids, boolean read) {
        Set<LibraryUpdateEventId> selected = Set.copyOf(
                Objects.requireNonNull(ids, "ids must not be null"));
        if (selected.isEmpty()) {
            return;
        }
        LibraryUpdateStore.State current = store.snapshot();
        store.replace(new LibraryUpdateStore.State(
                current.policy(),
                current.baselines(),
                current.events().stream()
                        .map(event -> selected.contains(event.id()) ? event.withRead(read) : event)
                        .toList(),
                current.lastRunAt()));
        discovered = discovered.stream()
                .map(event -> selected.contains(event.id()) ? event.withRead(read) : event)
                .toList();
        notifyListeners();
    }

    @Override
    public synchronized void removeEvents(Set<LibraryUpdateEventId> ids) {
        Set<LibraryUpdateEventId> selected = Set.copyOf(
                Objects.requireNonNull(ids, "ids must not be null"));
        if (selected.isEmpty()) {
            return;
        }
        LibraryUpdateStore.State current = store.snapshot();
        store.replace(new LibraryUpdateStore.State(
                current.policy(),
                current.baselines(),
                current.events().stream().filter(event -> !selected.contains(event.id())).toList(),
                current.lastRunAt()));
        discovered = discovered.stream().filter(event -> !selected.contains(event.id())).toList();
        notifyListeners();
    }

    public synchronized int cleanUnusedData() {
        ensureOpen();
        if (running != null && !running.isDone()) {
            throw new LibraryUpdateException("Unused update data cannot be cleaned during a library update");
        }
        Set<LibraryItemId> retainedIds = library.snapshot().stream()
                .map(LibraryItem::id)
                .collect(Collectors.toUnmodifiableSet());
        LibraryUpdateStore.State current = store.snapshot();
        Map<LibraryItemId, Set<String>> baselines = current.baselines().entrySet().stream()
                .filter(entry -> retainedIds.contains(entry.getKey()))
                .collect(Collectors.toUnmodifiableMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue));
        List<LibraryUpdateEvent> events = current.events().stream()
                .filter(event -> retainedIds.contains(event.libraryItemId()))
                .toList();
        Set<LibraryItemId> includedTitles = current.policy().includedTitles().stream()
                .filter(retainedIds::contains)
                .collect(Collectors.toUnmodifiableSet());
        Set<LibraryItemId> excludedTitles = current.policy().excludedTitles().stream()
                .filter(retainedIds::contains)
                .collect(Collectors.toUnmodifiableSet());
        LibraryUpdatePolicy cleanedPolicy = new LibraryUpdatePolicy(
                current.policy().interval(),
                current.policy().favoritesOnly(),
                current.policy().skipCompleted(),
                current.policy().skipNotStarted(),
                current.policy().includedCategories(),
                current.policy().excludedCategories(),
                includedTitles,
                excludedTitles);
        int removed = current.baselines().size() - baselines.size()
                + current.events().size() - events.size()
                + current.policy().includedTitles().size() - includedTitles.size()
                + current.policy().excludedTitles().size() - excludedTitles.size();
        if (removed > 0) {
            store.replace(new LibraryUpdateStore.State(
                    cleanedPolicy,
                    baselines,
                    events,
                    current.lastRunAt()));
            discovered = discovered.stream()
                    .filter(event -> retainedIds.contains(event.libraryItemId()))
                    .toList();
            notifyListeners();
        }
        return removed;
    }

    @Override
    public AutoCloseable observe(Runnable listener) {
        Runnable value = Objects.requireNonNull(listener, "listener must not be null");
        listeners.add(value);
        return () -> listeners.remove(value);
    }

    private LibraryUpdateSnapshot execute() {
        try {
            LibraryUpdateStore.State before = store.snapshot();
            List<LibraryItem> libraryItems = library.snapshot();
            Set<LibraryItemId> existingIds = libraryItems.stream()
                    .map(LibraryItem::id)
                    .collect(Collectors.toUnmodifiableSet());
            Map<LibraryItemId, Set<String>> baselines = new ConcurrentHashMap<>(before.baselines());
            baselines.keySet().retainAll(existingIds);
            List<LibraryItem> eligible = libraryItems.stream()
                    .filter(item -> skipReason(
                             item,
                             before.policy(),
                             libraryConfiguration.get()).isEmpty())
                    .sorted(Comparator.comparing(LibraryItem::title, String.CASE_INSENSITIVE_ORDER))
                    .toList();
            totalTitles = eligible.size();
            AtomicInteger completed = new AtomicInteger();
            Set<String> active = ConcurrentHashMap.newKeySet();
            CopyOnWriteArrayList<LibraryUpdateEvent> newEvents = new CopyOnWriteArrayList<>();
            CopyOnWriteArrayList<LibraryUpdateFailure> runFailures = new CopyOnWriteArrayList<>();
            Map<String, List<LibraryItem>> bySource = eligible.stream().collect(
                    Collectors.groupingBy(
                            item -> item.origin().orElseThrow().sourceId(),
                            LinkedHashMap::new,
                            Collectors.toList()));
            List<CompletableFuture<Void>> lanes = bySource.values().stream()
                    .map(items -> CompletableFuture.runAsync(
                            () -> processSource(items, baselines, newEvents, runFailures, active, completed),
                            workers))
                    .toList();
            CompletableFuture.allOf(lanes.toArray(CompletableFuture[]::new)).join();
            Instant finishedAt = clock.instant();
            List<LibraryUpdateEvent> mergedEvents = mergeEvents(newEvents, before.events());
            store.replace(new LibraryUpdateStore.State(
                    before.policy(),
                    baselines,
                    mergedEvents,
                    Optional.of(finishedAt)));
            discovered = List.copyOf(newEvents);
            failures = List.copyOf(runFailures);
            completedTitles = completed.get();
            activeTitles = List.of();
            status = cancellation.get() ? LibraryUpdateStatus.CANCELLED : LibraryUpdateStatus.COMPLETED;
            publish(new LibraryUpdateNotification(
                    LibraryUpdateNotificationType.CLEAR_PROGRESS, "", "", 0, 0));
            if (!newEvents.isEmpty()) {
                publish(new LibraryUpdateNotification(
                        LibraryUpdateNotificationType.NEW_CONTENT,
                        newEvents.size() + " new library update" + (newEvents.size() == 1 ? "" : "s"),
                        notificationSummary(newEvents),
                        0,
                        0));
            }
            if (!runFailures.isEmpty()) {
                publish(new LibraryUpdateNotification(
                        LibraryUpdateNotificationType.FAILURE,
                        runFailures.size() + " library update failure"
                                + (runFailures.size() == 1 ? "" : "s"),
                        failureSummary(runFailures),
                        0,
                        0));
            }
        } catch (RuntimeException exception) {
            failures = List.of(new LibraryUpdateFailure(
                    new LibraryItemId("library-update"),
                    "Library update",
                    Objects.toString(exception.getMessage(), exception.getClass().getSimpleName())));
            activeTitles = List.of();
            status = LibraryUpdateStatus.FAILED;
            publish(new LibraryUpdateNotification(
                    LibraryUpdateNotificationType.CLEAR_PROGRESS, "", "", 0, 0));
            publish(new LibraryUpdateNotification(
                    LibraryUpdateNotificationType.FAILURE,
                    "Library update failed",
                    failures.getFirst().message(),
                    0,
                    0));
        } finally {
            scheduleNext();
            notifyListeners();
        }
        return snapshot();
    }

    private void processSource(
            List<LibraryItem> items,
            Map<LibraryItemId, Set<String>> baselines,
            Collection<LibraryUpdateEvent> newEvents,
            Collection<LibraryUpdateFailure> runFailures,
            Set<String> active,
            AtomicInteger completed) {
        for (LibraryItem item : items) {
            if (cancellation.get() || Thread.currentThread().isInterrupted()) {
                return;
            }
            active.add(item.title());
            updateProgress(active, completed.get());
            try {
                List<ContentRecord> content = fetch(item);
                Set<String> currentIds = content.stream()
                        .map(ContentRecord::id)
                        .collect(Collectors.toCollection(LinkedHashSet::new));
                Set<String> previous = baselines.put(item.id(), Set.copyOf(currentIds));
                if (previous != null) {
                    Instant discoveredAt = clock.instant();
                    content.stream()
                            .filter(record -> !previous.contains(record.id()))
                            .map(record -> new LibraryUpdateEvent(
                                    item.id(), item.title(), item.kind(), record.id(), record.title(),
                                    record.publishedAt(), discoveredAt, false))
                            .forEach(newEvents::add);
                }
            } catch (RuntimeException exception) {
                runFailures.add(new LibraryUpdateFailure(
                        item.id(),
                        item.title(),
                        Objects.toString(exception.getMessage(), exception.getClass().getSimpleName())));
            } finally {
                active.remove(item.title());
                updateProgress(active, completed.incrementAndGet());
            }
        }
    }

    private List<ContentRecord> fetch(LibraryItem item) {
        LibraryOrigin origin = item.origin().orElseThrow();
        SourceId sourceId = SourceId.of(origin.sourceId());
        Source source = sources.find(sourceId).orElseThrow(
                () -> new LibraryUpdateException("Source is not installed: " + sourceId));
        SourceCatalogueItemId itemId = new SourceCatalogueItemId(sourceId, origin.sourceItemKey());
        List<ContentRecord> content;
        if (item.kind() == MediaKind.ANIME && source instanceof StreamingSource streaming) {
            content = streaming.episodes(itemId).stream().map(DefaultLibraryUpdateService::episode).toList();
        } else if (source instanceof PagedSource paged) {
            content = paged.contentUnits(itemId).stream().map(DefaultLibraryUpdateService::unit).toList();
        } else if (source instanceof StreamingSource streaming) {
            content = streaming.episodes(itemId).stream().map(DefaultLibraryUpdateService::episode).toList();
        } else {
            throw new LibraryUpdateException("Source does not expose refreshable content: " + sourceId);
        }
        Map<String, ContentRecord> unique = new LinkedHashMap<>();
        for (ContentRecord record : content) {
            if (unique.putIfAbsent(record.id(), record) != null) {
                throw new LibraryUpdateException("Source returned duplicate content identity: " + record.id());
            }
        }
        return List.copyOf(unique.values());
    }

    private void updateProgress(Set<String> active, int completed) {
        completedTitles = completed;
        activeTitles = active.stream().sorted(String.CASE_INSENSITIVE_ORDER).toList();
        notifyListeners();
        publish(new LibraryUpdateNotification(
                LibraryUpdateNotificationType.PROGRESS,
                "Updating library",
                activeTitles.isEmpty() ? completed + " of " + totalTitles : String.join(", ", activeTitles),
                completed,
                totalTitles));
    }

    private static Optional<LibraryUpdateSkipReason> skipReason(
            LibraryItem item,
            LibraryUpdatePolicy policy,
            LibraryConfigurationSnapshot configuration) {
        if (item.origin().isEmpty()) {
            return Optional.of(LibraryUpdateSkipReason.NO_SOURCE_ORIGIN);
        }
        if (policy.includedTitles().contains(item.id())) {
            return Optional.empty();
        }
        if (policy.excludedTitles().contains(item.id())) {
            return Optional.of(LibraryUpdateSkipReason.TITLE_EXCLUDED);
        }
        if (policy.favoritesOnly() && !item.favorite()) {
            return Optional.of(LibraryUpdateSkipReason.NOT_FAVORITE);
        }
        if (policy.skipCompleted()
                && item.metadata().publicationStatus() == PublicationStatus.COMPLETED) {
            return Optional.of(LibraryUpdateSkipReason.PUBLICATION_COMPLETED);
        }
        if (policy.skipNotStarted() && item.progress().isEmpty()) {
            return Optional.of(LibraryUpdateSkipReason.NOT_STARTED);
        }
        Map<String, LibraryCategoryUpdatePolicy> categoryPolicies = configuration.categories()
                .stream()
                .collect(Collectors.toMap(
                        LibraryCategory::name,
                        LibraryCategory::updatePolicy));
        boolean explicitlyIncluded = item.categories().stream()
                .map(categoryPolicies::get)
                .anyMatch(LibraryCategoryUpdatePolicy.INCLUDE::equals);
        boolean explicitlyExcluded = item.categories().stream()
                .map(categoryPolicies::get)
                .anyMatch(LibraryCategoryUpdatePolicy.EXCLUDE::equals);
        if (explicitlyExcluded) {
            return Optional.of(LibraryUpdateSkipReason.CATEGORY_EXCLUDED);
        }
        if (explicitlyIncluded) {
            return Optional.empty();
        }
        if (!policy.includedCategories().isEmpty()
                && Collections.disjoint(item.categories(), policy.includedCategories())) {
            return Optional.of(LibraryUpdateSkipReason.CATEGORY_NOT_INCLUDED);
        }
        return Collections.disjoint(item.categories(), policy.excludedCategories())
                ? Optional.empty()
                : Optional.of(LibraryUpdateSkipReason.CATEGORY_EXCLUDED);
    }

    private List<LibraryUpdateSkip> skippedTitles(LibraryUpdatePolicy policy) {
        LibraryConfigurationSnapshot configuration = libraryConfiguration.get();
        return library.snapshot().stream()
                .map(item -> skipReason(item, policy, configuration)
                        .map(reason -> new LibraryUpdateSkip(item.id(), item.title(), reason)))
                .flatMap(Optional::stream)
                .sorted(Comparator.comparing(LibraryUpdateSkip::title, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private static List<LibraryUpdateEvent> mergeEvents(
            Collection<LibraryUpdateEvent> discovered,
            Collection<LibraryUpdateEvent> existing) {
        Map<EventKey, LibraryUpdateEvent> merged = new LinkedHashMap<>();
        discovered.forEach(event -> merged.put(EventKey.of(event), event));
        existing.forEach(event -> merged.putIfAbsent(EventKey.of(event), event));
        return merged.values().stream().sorted(EVENT_ORDER).limit(MAXIMUM_VISIBLE_EVENTS).toList();
    }

    private static ContentRecord episode(SourceEpisode episode) {
        return new ContentRecord(
                episode.id().value(),
                episode.title(),
                episode.uploadedAt());
    }

    private static ContentRecord unit(SourceContentUnit unit) {
        return new ContentRecord(unit.id().value(), unit.title(), unit.publishedAt());
    }

    private static String notificationSummary(Collection<LibraryUpdateEvent> events) {
        return events.stream()
                .map(LibraryUpdateEvent::libraryTitle)
                .distinct()
                .limit(3)
                .collect(Collectors.joining(", "));
    }

    private static String failureSummary(Collection<LibraryUpdateFailure> failures) {
        return failures.stream()
                .map(LibraryUpdateFailure::title)
                .distinct()
                .limit(3)
                .collect(Collectors.joining(", "));
    }

    private synchronized void scheduleNext() {
        if (closed) {
            return;
        }
        if (scheduled != null) {
            scheduled.cancel(false);
            scheduled = null;
        }
        LibraryUpdateStore.State durable = store.snapshot();
        UpdateInterval interval = durable.policy().interval();
        if (interval == UpdateInterval.MANUAL) {
            nextRunAt = Optional.empty();
            return;
        }
        Instant base = durable.lastRunAt().orElse(clock.instant());
        Instant next = base.plus(interval.duration());
        Instant now = clock.instant();
        Duration delay = Duration.between(now, next);
        if (delay.isNegative()) {
            delay = Duration.ZERO;
            next = now;
        }
        nextRunAt = Optional.of(next);
        scheduled = scheduler.schedule(this::runScheduled, delay.toMillis(), TimeUnit.MILLISECONDS);
    }

    private void runScheduled() {
        try {
            runNow();
        } catch (IllegalStateException ignored) {
            // Product shutdown won the race with the scheduled callback.
        }
    }

    private void publish(LibraryUpdateNotification notification) {
        if (!notifier.available()) {
            return;
        }
        try {
            notifier.publish(notification);
        } catch (RuntimeException ignored) {
            // A system notification failure must not corrupt the durable update run.
        }
    }

    private void notifyListeners() {
        listeners.forEach(Runnable::run);
    }

    private synchronized void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("Library update service is closed");
        }
    }

    LibraryUpdateStore stateStore() {
        return store;
    }

    synchronized void replaceState(LibraryUpdateStore.State replacement) {
        ensureOpen();
        store.replace(replacement);
        scheduleNext();
        notifyListeners();
    }

    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        closed = true;
        cancellation.set(true);
        if (scheduled != null) {
            scheduled.cancel(false);
        }
        scheduler.shutdownNow();
        coordinator.shutdownNow();
        workers.shutdownNow();
        notifier.close();
        listeners.clear();
    }

    private static Thread daemon(Runnable task, String name) {
        Thread thread = new Thread(task, name);
        thread.setDaemon(true);
        return thread;
    }

    private record ContentRecord(String id, String title, Optional<Instant> publishedAt) {
        private ContentRecord {
            Objects.requireNonNull(id, "id must not be null");
            Objects.requireNonNull(title, "title must not be null");
            Objects.requireNonNull(publishedAt, "publishedAt must not be null");
        }
    }

    private record EventKey(LibraryItemId itemId, String contentId) {
        private static EventKey of(LibraryUpdateEvent event) {
            return new EventKey(event.libraryItemId(), event.sourceContentId());
        }
    }
}
