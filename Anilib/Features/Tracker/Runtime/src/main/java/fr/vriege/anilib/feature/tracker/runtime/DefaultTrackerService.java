package fr.vriege.anilib.feature.tracker.runtime;

import fr.vriege.anilib.feature.library.LibraryCatalog;
import fr.vriege.anilib.feature.library.LibraryItem;
import fr.vriege.anilib.feature.library.LibraryItemId;
import fr.vriege.anilib.feature.library.MediaKind;
import fr.vriege.anilib.feature.tracker.Tracker;
import fr.vriege.anilib.feature.tracker.TrackerAccount;
import fr.vriege.anilib.feature.tracker.TrackerAuthentication;
import fr.vriege.anilib.feature.tracker.TrackerCredentials;
import fr.vriege.anilib.feature.tracker.TrackerEntry;
import fr.vriege.anilib.feature.tracker.TrackerException;
import fr.vriege.anilib.feature.tracker.TrackerId;
import fr.vriege.anilib.feature.tracker.TrackerRegistry;
import fr.vriege.anilib.feature.tracker.TrackerSearchResult;
import fr.vriege.anilib.feature.tracker.TrackerService;
import fr.vriege.anilib.feature.tracker.TrackerStatus;

import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

/** Durable orchestration for authentication, binding, editing, refresh, and progress sync. */
public final class DefaultTrackerService implements TrackerService {
    private final TrackerRegistry registry;
    private final LibraryCatalog library;
    private final TrackerEntryStore entries;
    private final CopyOnWriteArrayList<Runnable> listeners = new CopyOnWriteArrayList<>();

    public DefaultTrackerService(TrackerRegistry registry, LibraryCatalog library, Path stateFile) {
        this.registry = Objects.requireNonNull(registry, "registry must not be null");
        this.library = Objects.requireNonNull(library, "library must not be null");
        entries = new TrackerEntryStore(stateFile);
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
                update(replacement);
            } catch (RuntimeException exception) {
                failures.add(exception);
            }
        }
        if (!failures.isEmpty()) {
            TrackerException failure = new TrackerException("One or more tracker updates failed");
            failures.forEach(failure::addSuppressed);
            throw failure;
        }
    }

    @Override
    public List<TrackerEntry> snapshot() {
        return entries.snapshot();
    }

    @Override
    public void replaceAll(Collection<TrackerEntry> replacement) {
        entries.replaceAll(replacement);
        notifyListeners();
    }

    @Override
    public AutoCloseable observe(Runnable listener) {
        Runnable value = Objects.requireNonNull(listener, "listener must not be null");
        listeners.add(value);
        return () -> listeners.remove(value);
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
}
