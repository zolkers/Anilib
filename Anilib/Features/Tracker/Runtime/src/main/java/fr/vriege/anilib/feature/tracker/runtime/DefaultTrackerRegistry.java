package fr.vriege.anilib.feature.tracker.runtime;

import fr.vriege.anilib.feature.tracker.InstalledTrackerExtension;
import fr.vriege.anilib.feature.tracker.Tracker;
import fr.vriege.anilib.feature.tracker.TrackerDescriptor;
import fr.vriege.anilib.feature.tracker.TrackerException;
import fr.vriege.anilib.feature.tracker.TrackerExtensionManifest;
import fr.vriege.anilib.feature.tracker.TrackerId;
import fr.vriege.anilib.feature.tracker.TrackerRegistrar;
import fr.vriege.anilib.feature.tracker.TrackerRegistration;
import fr.vriege.anilib.feature.tracker.TrackerRegistry;
import fr.vriege.anilib.feature.tracker.TrackerSdk;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

public final class DefaultTrackerRegistry implements TrackerRegistry, TrackerRegistrar, AutoCloseable {
    private final TreeMap<TrackerId, Entry> trackers = new TreeMap<>();
    private boolean closed;

    public DefaultTrackerRegistry() {
    }

    @Override
    public synchronized TrackerRegistration register(
            TrackerExtensionManifest manifest,
            Tracker tracker) {
        ensureOpen();
        TrackerExtensionManifest extension = Objects.requireNonNull(
                manifest,
                "manifest must not be null");
        Tracker value = Objects.requireNonNull(tracker, "tracker must not be null");
        TrackerDescriptor descriptor = Objects.requireNonNull(
                value.descriptor(),
                "tracker descriptor must not be null");
        if (!extension.trackerId().equals(descriptor.id())) {
            throw new TrackerException("Tracker extension identity does not match its descriptor");
        }
        if (!TrackerSdk.API_VERSION.supports(descriptor.requiredApiVersion())) {
            throw new TrackerException(
                    "Tracker " + descriptor.id() + " requires API " + descriptor.requiredApiVersion()
                            + " but Anilib provides " + TrackerSdk.API_VERSION);
        }
        Entry entry = new Entry(extension, value);
        if (trackers.putIfAbsent(descriptor.id(), entry) != null) {
            throw new TrackerException("Duplicate tracker id: " + descriptor.id());
        }
        return new Registration(descriptor.id(), entry);
    }

    @Override
    public synchronized List<Tracker> trackers() {
        ensureOpen();
        return trackers.values().stream().map(Entry::tracker).toList();
    }

    @Override
    public synchronized List<InstalledTrackerExtension> extensions() {
        ensureOpen();
        return trackers.values().stream()
                .map(entry -> new InstalledTrackerExtension(entry.manifest(), entry.tracker().descriptor()))
                .toList();
    }

    @Override
    public synchronized Optional<Tracker> find(TrackerId id) {
        ensureOpen();
        Entry entry = trackers.get(Objects.requireNonNull(id, "id must not be null"));
        return entry == null ? Optional.empty() : Optional.of(entry.tracker());
    }

    @Override
    public synchronized void close() {
        if (!closed) {
            closed = true;
            trackers.clear();
        }
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("Tracker registry is closed");
        }
    }

    private record Entry(TrackerExtensionManifest manifest, Tracker tracker) {
    }

    private final class Registration implements TrackerRegistration {
        private final TrackerId id;
        private final Entry entry;
        private boolean removed;

        private Registration(TrackerId id, Entry entry) {
            this.id = id;
            this.entry = entry;
        }

        @Override
        public TrackerId trackerId() {
            return id;
        }

        @Override
        public void close() {
            synchronized (DefaultTrackerRegistry.this) {
                if (!removed && !closed) {
                    trackers.remove(id, entry);
                    removed = true;
                }
            }
        }
    }
}
