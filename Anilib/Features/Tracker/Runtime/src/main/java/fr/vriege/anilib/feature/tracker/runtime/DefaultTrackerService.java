package fr.vriege.anilib.feature.tracker.runtime;

import fr.vriege.anilib.feature.library.LibraryCatalog;
import fr.vriege.anilib.feature.library.LibraryItem;
import fr.vriege.anilib.feature.library.LibraryItemId;
import fr.vriege.anilib.feature.library.MediaKind;
import fr.vriege.anilib.feature.tracker.Tracker;
import fr.vriege.anilib.feature.tracker.TrackerAccount;
import fr.vriege.anilib.feature.tracker.TrackerAuthentication;
import fr.vriege.anilib.feature.tracker.TrackerCredentials;
import fr.vriege.anilib.feature.tracker.TrackerConflictPolicy;
import fr.vriege.anilib.feature.tracker.TrackerConflictResolution;
import fr.vriege.anilib.feature.tracker.TrackerEntry;
import fr.vriege.anilib.feature.tracker.TrackerException;
import fr.vriege.anilib.feature.tracker.TrackerId;
import fr.vriege.anilib.feature.tracker.TrackerRegistry;
import fr.vriege.anilib.feature.tracker.TrackerSearchResult;
import fr.vriege.anilib.feature.tracker.TrackerService;
import fr.vriege.anilib.feature.tracker.TrackerStatus;
import fr.vriege.anilib.feature.tracker.TrackerSyncConflict;
import fr.vriege.anilib.feature.tracker.TrackerSyncDirection;
import fr.vriege.anilib.feature.tracker.TrackerSyncPreferences;
import fr.vriege.anilib.feature.tracker.TrackerSyncReport;

import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public final class DefaultTrackerService implements TrackerService, AutoCloseable {
    private final TrackerRegistry registry;
    private final LibraryCatalog library;
    private final TrackerEntryStore entries;
    private final TrackerSyncPreferenceStore preferences;
    private final TrackerPendingSyncStore pendingSynchronizations;
    private final CopyOnWriteArrayList<Runnable> listeners = new CopyOnWriteArrayList<>();
    private final Map<BindingKey, TrackerSyncConflict> conflicts = new LinkedHashMap<>();
    private final Set<BindingKey> dirtyEntries = new HashSet<>();
    private final ExecutorService synchronizer = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "anilib-tracker-sync");
        thread.setDaemon(true);
        return thread;
    });
    private final AtomicBoolean synchronizationQueued = new AtomicBoolean();
    private final AutoCloseable libraryObservation;
    private volatile boolean closed;

    public DefaultTrackerService(TrackerRegistry registry, LibraryCatalog library, Path stateFile) {
        this.registry = Objects.requireNonNull(registry, "registry must not be null");
        this.library = Objects.requireNonNull(library, "library must not be null");
        Path file = Objects.requireNonNull(stateFile, "stateFile must not be null").toAbsolutePath().normalize();
        entries = new TrackerEntryStore(file);
        preferences = new TrackerSyncPreferenceStore(file.resolveSibling("tracking-sync.properties"));
        pendingSynchronizations = new TrackerPendingSyncStore(
                file.resolveSibling("tracking-sync-pending.anilib"));
        pendingSynchronizations.load().forEach(value -> dirtyEntries.add(
                new BindingKey(value.libraryItemId(), value.trackerId())));
        libraryObservation = library.observe(this::libraryChanged);
    }

    @Override
    public List<TrackerAccount> accounts() {
        return registry.trackers().stream()
                .map(tracker -> new TrackerAccount(
                        tracker.descriptor(),
                        tracker.descriptor().authentication() == TrackerAuthentication.NONE
                                || tracker.isAuthenticated(),
                        tracker.accountName()))
                .toList();
    }

    @Override
    public void authenticate(TrackerId trackerId, TrackerCredentials credentials) {
        Tracker tracker = tracker(trackerId);
        TrackerCredentials value = Objects.requireNonNull(credentials, "credentials must not be null");
        if (tracker.descriptor().authentication() != value.authentication()) {
            throw new TrackerException("Credentials do not match " + tracker.descriptor().authentication());
        }
        tracker.authenticate(value);
        if (!tracker.isAuthenticated()) {
            throw new TrackerException("Tracker did not establish an authenticated session");
        }
        notifyListeners();
        queueAutomaticSynchronization();
    }

    @Override
    public void logout(TrackerId trackerId) {
        tracker(trackerId).logout();
        notifyListeners();
    }

    @Override
    public List<TrackerSearchResult> search(TrackerId trackerId, String query, MediaKind kind) {
        Tracker tracker = readyTracker(trackerId);
        String value = Objects.requireNonNull(query, "query must not be null").strip();
        if (value.isEmpty()) {
            throw new IllegalArgumentException("query must not be blank");
        }
        if (!tracker.descriptor().supportedKinds().contains(kind)) {
            throw new TrackerException("Tracker does not support " + kind);
        }
        List<TrackerSearchResult> results = List.copyOf(tracker.search(value, kind));
        if (results.stream().anyMatch(result -> !result.trackerId().equals(trackerId)
                || result.kind() != kind)) {
            throw new TrackerException("Tracker returned a result outside its declared identity or media kind");
        }
        return results;
    }

    @Override
    public List<TrackerEntry> entries(LibraryItemId libraryItemId) {
        return entries.forItem(Objects.requireNonNull(libraryItemId, "libraryItemId must not be null"));
    }

    @Override
    public TrackerEntry bind(LibraryItemId libraryItemId, TrackerSearchResult result) {
        LibraryItem item = library.find(Objects.requireNonNull(
                libraryItemId,
                "libraryItemId must not be null")).orElseThrow(
                        () -> new TrackerException("Library title no longer exists"));
        TrackerSearchResult candidate = Objects.requireNonNull(result, "result must not be null");
        Tracker tracker = readyTracker(candidate.trackerId());
        if (candidate.kind() != item.kind()) {
            throw new TrackerException("Tracker result media kind does not match the library title");
        }
        if (entries.find(libraryItemId, candidate.trackerId()).isPresent()) {
            throw new TrackerException("Title is already bound to " + tracker.descriptor().name());
        }
        TrackerEntry bound = validate(tracker, tracker.bind(item, candidate), libraryItemId);
        entries.save(bound);
        notifyListeners();
        return bound;
    }

    @Override
    public TrackerEntry update(TrackerEntry entry) {
        TrackerEntry requested = Objects.requireNonNull(entry, "entry must not be null");
        Tracker tracker = readyTracker(requested.trackerId());
        if (entries.find(requested.libraryItemId(), requested.trackerId()).isEmpty()) {
            throw new TrackerException("Tracker entry is not bound");
        }
        TrackerEntry current = entries.find(requested.libraryItemId(), requested.trackerId()).orElseThrow();
        if (!current.remoteId().equals(requested.remoteId())) {
            throw new TrackerException("Tracker update cannot change the remote identity");
        }
        TrackerEntry normalized = normalizeProgress(tracker, current, requested);
        validateEditable(tracker, normalized);
        TrackerEntry updated = validate(
                tracker,
                tracker.update(normalized),
                requested.libraryItemId());
        entries.save(updated);
        clearPending(BindingKey.of(updated));
        notifyListeners();
        return updated;
    }

    @Override
    public TrackerEntry refresh(LibraryItemId libraryItemId, TrackerId trackerId) {
        Tracker tracker = readyTracker(trackerId);
        TrackerEntry current = entries.find(libraryItemId, trackerId).orElseThrow(
                () -> new TrackerException("Tracker entry is not bound"));
        TrackerEntry refreshed = validate(tracker, tracker.refresh(current), libraryItemId);
        entries.save(refreshed);
        clearPending(BindingKey.of(refreshed));
        notifyListeners();
        return refreshed;
    }

    @Override
    public boolean remove(LibraryItemId libraryItemId, TrackerId trackerId) {
        Optional<TrackerEntry> current = entries.find(libraryItemId, trackerId);
        if (current.isEmpty()) {
            return false;
        }
        readyTracker(trackerId).remove(current.orElseThrow());
        boolean removed = entries.remove(libraryItemId, trackerId);
        clearPending(new BindingKey(libraryItemId, trackerId));
        notifyListeners();
        return removed;
    }

    @Override
    public void synchronizeProgress(LibraryItemId libraryItemId, double progress, long totalUnits) {
        if (!Double.isFinite(progress) || progress < 0.0 || totalUnits < TrackerSearchResult.UNKNOWN_TOTAL) {
            throw new IllegalArgumentException("progress or totalUnits is invalid");
        }
        List<RuntimeException> failures = new ArrayList<>();
        for (TrackerEntry current : entries.forItem(libraryItemId)) {
            try {
                double bounded = totalUnits >= 0 ? Math.min(progress, totalUnits) : progress;
                TrackerStatus status = totalUnits >= 0 && bounded == totalUnits
                        ? TrackerStatus.COMPLETED
                        : current.status();
                TrackerEntry replacement = new TrackerEntry(
                        current.libraryItemId(), current.trackerId(), current.remoteId(), current.title(),
                        bounded, totalUnits, status, current.score(), current.startDate(),
                        status == TrackerStatus.COMPLETED && current.finishDate().isEmpty()
                                ? Optional.of(LocalDate.now()) : current.finishDate(),
                        current.privateEntry(), current.remoteUri(), Instant.now());
                if (syncPreferences().automatic()) {
                    update(replacement);
                } else {
                    entries.save(replacement);
                    synchronized (this) {
                        dirtyEntries.add(BindingKey.of(replacement));
                        persistPending();
                    }
                    notifyListeners();
                }
            } catch (RuntimeException exception) {
                failures.add(exception);
            }
        }
        if (!failures.isEmpty()) {
            TrackerException failure = new TrackerException("One or more tracker updates failed");
            failures.forEach(failure::addSuppressed);
            throw failure;
        }
        queueAutomaticSynchronization();
    }

    @Override
    public TrackerSyncPreferences syncPreferences() {
        return preferences.load();
    }

    @Override
    public void saveSyncPreferences(TrackerSyncPreferences value) {
        preferences.save(Objects.requireNonNull(value, "preferences must not be null"));
        notifyListeners();
        queueAutomaticSynchronization();
    }

    @Override
    public synchronized TrackerSyncReport synchronizeAll() {
        SyncCounts counts = new SyncCounts();
        for (TrackerEntry entry : entries.snapshot()) {
            synchronizeEntry(entry, counts);
        }
        if (counts.changed()) {
            notifyListeners();
        }
        return counts.report();
    }

    @Override
    public synchronized TrackerSyncReport synchronize(LibraryItemId libraryItemId) {
        LibraryItemId itemId = Objects.requireNonNull(libraryItemId, "libraryItemId must not be null");
        SyncCounts counts = new SyncCounts();
        for (TrackerEntry entry : entries.forItem(itemId)) {
            synchronizeEntry(entry, counts);
        }
        if (counts.changed()) {
            notifyListeners();
        }
        return counts.report();
    }

    @Override
    public synchronized List<TrackerSyncConflict> conflicts() {
        return List.copyOf(conflicts.values());
    }

    @Override
    public synchronized TrackerEntry resolveConflict(
            LibraryItemId libraryItemId,
            TrackerId trackerId,
            TrackerConflictResolution resolution) {
        BindingKey key = new BindingKey(
                Objects.requireNonNull(libraryItemId, "libraryItemId must not be null"),
                Objects.requireNonNull(trackerId, "trackerId must not be null"));
        TrackerSyncConflict conflict = Optional.ofNullable(conflicts.get(key)).orElseThrow(
                () -> new TrackerException("Tracker synchronization conflict no longer exists"));
        TrackerEntry result = switch (Objects.requireNonNull(resolution, "resolution must not be null")) {
            case KEEP_LOCAL -> push(tracker(trackerId), conflict.localEntry());
            case KEEP_REMOTE -> pull(conflict.remoteEntry());
        };
        clearPending(key);
        notifyListeners();
        return result;
    }

    @Override
    public List<TrackerEntry> snapshot() {
        return entries.snapshot();
    }

    @Override
    public void replaceAll(Collection<TrackerEntry> replacement) {
        entries.replaceAll(replacement);
        synchronized (this) {
            dirtyEntries.clear();
            replacement.forEach(entry -> dirtyEntries.add(BindingKey.of(entry)));
            conflicts.clear();
            persistPending();
        }
        notifyListeners();
    }

    public int cleanUnusedData() {
        List<TrackerEntry> current = entries.snapshot();
        List<TrackerEntry> retained = current.stream()
                .filter(entry -> library.find(entry.libraryItemId()).isPresent())
                .toList();
        int removed = current.size() - retained.size();
        if (removed > 0) {
            entries.replaceAll(retained);
            synchronized (this) {
                Set<BindingKey> retainedKeys = retained.stream().map(BindingKey::of).collect(
                        java.util.stream.Collectors.toSet());
                dirtyEntries.retainAll(retainedKeys);
                conflicts.keySet().retainAll(retainedKeys);
                persistPending();
            }
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

    @Override
    public void close() {
        closed = true;
        try {
            libraryObservation.close();
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to close tracker library observation", exception);
        } finally {
            synchronizer.shutdownNow();
        }
    }

    private void synchronizeEntry(TrackerEntry current, SyncCounts counts) {
        Tracker tracker;
        try {
            tracker = readyTracker(current.trackerId());
            TrackerEntry remote = validate(tracker, tracker.refresh(current), current.libraryItemId());
            BindingKey key = BindingKey.of(current);
            if (sameEditableState(current, remote)) {
                entries.save(remote);
                clearPending(key);
                counts.pulled++;
                return;
            }
            boolean dirty = dirtyEntries.contains(key);
            TrackerSyncPreferences policy = syncPreferences();
            if (!dirty) {
                if (policy.direction() != TrackerSyncDirection.PUSH_ONLY) {
                    pull(remote);
                    counts.pulled++;
                }
                return;
            }
            if (policy.direction() == TrackerSyncDirection.PUSH_ONLY) {
                push(tracker, current);
                clearPending(key);
                counts.pushed++;
                return;
            }
            if (policy.direction() == TrackerSyncDirection.PULL_ONLY) {
                pull(remote);
                clearPending(key);
                counts.pulled++;
                return;
            }
            resolvePolicy(tracker, current, remote, policy.conflictPolicy(), counts);
        } catch (RuntimeException exception) {
            String message = exception.getMessage();
            counts.failures.add(current.trackerId().value() + ": "
                    + (message == null || message.isBlank() ? exception.getClass().getSimpleName() : message));
        }
    }

    private void resolvePolicy(
            Tracker tracker,
            TrackerEntry local,
            TrackerEntry remote,
            TrackerConflictPolicy policy,
            SyncCounts counts) {
        BindingKey key = BindingKey.of(local);
        switch (policy) {
            case KEEP_LOCAL -> {
                push(tracker, local);
                clearPending(key);
                counts.pushed++;
            }
            case KEEP_REMOTE -> {
                pull(remote);
                clearPending(key);
                counts.pulled++;
            }
            case NEWEST_WINS -> {
                if (local.updatedAt().isAfter(remote.updatedAt())) {
                    push(tracker, local);
                    counts.pushed++;
                } else {
                    pull(remote);
                    counts.pulled++;
                }
                clearPending(key);
            }
            case ASK -> {
                TrackerSyncConflict conflict = new TrackerSyncConflict(local, remote, Instant.now());
                conflicts.put(key, conflict);
                counts.conflicts.add(conflict);
            }
        }
    }

    private TrackerEntry push(Tracker tracker, TrackerEntry local) {
        validateEditable(tracker, local);
        TrackerEntry pushed = validate(tracker, tracker.update(local), local.libraryItemId());
        entries.save(pushed);
        return pushed;
    }

    private TrackerEntry pull(TrackerEntry remote) {
        entries.save(remote);
        return remote;
    }

    private synchronized void clearPending(BindingKey key) {
        boolean changed = dirtyEntries.remove(key);
        conflicts.remove(key);
        if (changed) {
            persistPending();
        }
    }

    private synchronized void persistPending() {
        pendingSynchronizations.save(dirtyEntries.stream()
                .map(key -> new TrackerPendingSyncStore.PendingSync(key.libraryItemId(), key.trackerId()))
                .toList());
    }

    private void libraryChanged() {
        queueAutomaticSynchronization();
    }

    private void queueAutomaticSynchronization() {
        if (closed || !syncPreferences().automatic() || !synchronizationQueued.compareAndSet(false, true)) {
            return;
        }
        synchronizer.execute(() -> {
            try {
                synchronizeAll();
            } finally {
                synchronizationQueued.set(false);
            }
        });
    }

    private static boolean sameEditableState(TrackerEntry first, TrackerEntry second) {
        return first.progress() == second.progress()
                && first.totalUnits() == second.totalUnits()
                && first.status() == second.status()
                && first.score().equals(second.score())
                && first.startDate().equals(second.startDate())
                && first.finishDate().equals(second.finishDate())
                && first.privateEntry() == second.privateEntry()
                && first.title().equals(second.title())
                && first.remoteUri().equals(second.remoteUri());
    }

    private Tracker tracker(TrackerId id) {
        return registry.find(Objects.requireNonNull(id, "trackerId must not be null")).orElseThrow(
                () -> new TrackerException("Tracker is not installed: " + id));
    }

    private Tracker readyTracker(TrackerId id) {
        Tracker tracker = tracker(id);
        if (tracker.descriptor().authentication() != TrackerAuthentication.NONE
                && !tracker.isAuthenticated()) {
            throw new TrackerException("Tracker account is not authenticated: " + id);
        }
        return tracker;
    }

    private static TrackerEntry validate(Tracker tracker, TrackerEntry entry, LibraryItemId itemId) {
        TrackerEntry value = Objects.requireNonNull(entry, "tracker must not return null");
        if (!value.trackerId().equals(tracker.descriptor().id())
                || !value.libraryItemId().equals(itemId)) {
            throw new TrackerException("Tracker returned an entry outside its bound identity");
        }
        validateEditable(tracker, value);
        return value;
    }

    private static void validateEditable(Tracker tracker, TrackerEntry entry) {
        if (!tracker.descriptor().statuses().contains(entry.status())) {
            throw new TrackerException("Tracker does not support status " + entry.status());
        }
        if (entry.score().isPresent() && !tracker.descriptor().scores().contains(entry.score().getAsDouble())) {
            throw new TrackerException("Tracker does not support score " + entry.score().getAsDouble());
        }
        if (!tracker.descriptor().supportsDates()
                && (entry.startDate().isPresent() || entry.finishDate().isPresent())) {
            throw new TrackerException("Tracker does not support activity dates");
        }
        if (!tracker.descriptor().supportsPrivateEntries() && entry.privateEntry()) {
            throw new TrackerException("Tracker does not support private entries");
        }
    }

    private static TrackerEntry normalizeProgress(
            Tracker tracker,
            TrackerEntry current,
            TrackerEntry requested) {
        TrackerStatus status = requested.status();
        Optional<LocalDate> start = requested.startDate();
        Optional<LocalDate> finish = requested.finishDate();
        if (requested.progress() > current.progress() && status == TrackerStatus.PLANNING) {
            if (tracker.descriptor().statuses().contains(TrackerStatus.WATCHING)) {
                status = TrackerStatus.WATCHING;
            } else if (tracker.descriptor().statuses().contains(TrackerStatus.READING)) {
                status = TrackerStatus.READING;
            }
            if (tracker.descriptor().supportsDates() && start.isEmpty()) {
                start = Optional.of(LocalDate.now());
            }
        }
        if (requested.totalUnits() >= 0 && requested.progress() == requested.totalUnits()
                && tracker.descriptor().statuses().contains(TrackerStatus.COMPLETED)) {
            status = TrackerStatus.COMPLETED;
            if (tracker.descriptor().supportsDates() && finish.isEmpty()) {
                finish = Optional.of(LocalDate.now());
            }
        }
        if (status == requested.status()
                && start.equals(requested.startDate())
                && finish.equals(requested.finishDate())) {
            return requested;
        }
        return new TrackerEntry(
                requested.libraryItemId(), requested.trackerId(), requested.remoteId(), requested.title(),
                requested.progress(), requested.totalUnits(), status, requested.score(), start, finish,
                requested.privateEntry(), requested.remoteUri(), requested.updatedAt());
    }

    private void notifyListeners() {
        listeners.forEach(Runnable::run);
    }

    private record BindingKey(LibraryItemId libraryItemId, TrackerId trackerId) {
        private BindingKey {
            Objects.requireNonNull(libraryItemId, "libraryItemId must not be null");
            Objects.requireNonNull(trackerId, "trackerId must not be null");
        }

        private static BindingKey of(TrackerEntry entry) {
            return new BindingKey(entry.libraryItemId(), entry.trackerId());
        }
    }

    private static final class SyncCounts {
        private int pushed;
        private int pulled;
        private final List<TrackerSyncConflict> conflicts = new ArrayList<>();
        private final List<String> failures = new ArrayList<>();

        private boolean changed() {
            return pushed > 0 || pulled > 0 || !conflicts.isEmpty();
        }

        private TrackerSyncReport report() {
            return new TrackerSyncReport(pushed, pulled, conflicts, failures);
        }
    }
}
