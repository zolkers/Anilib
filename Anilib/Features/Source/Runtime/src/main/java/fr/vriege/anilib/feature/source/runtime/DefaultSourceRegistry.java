package fr.vriege.anilib.feature.source.runtime;

import fr.vriege.anilib.feature.source.Source;
import fr.vriege.anilib.feature.source.SourceDescriptor;
import fr.vriege.anilib.feature.source.SourceExtensionManifest;
import fr.vriege.anilib.feature.source.SourceId;
import fr.vriege.anilib.feature.source.InstalledSourceExtension;
import fr.vriege.anilib.feature.source.SourceRegistrar;
import fr.vriege.anilib.feature.source.SourceRegistration;
import fr.vriege.anilib.feature.source.SourceRegistrationException;
import fr.vriege.anilib.feature.source.SourceRegistry;
import fr.vriege.anilib.feature.source.SourceSdk;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

public final class DefaultSourceRegistry implements SourceRegistry, SourceRegistrar, AutoCloseable {
    private final TreeMap<SourceId, Entry> sources = new TreeMap<>();
    private boolean closed;

    public DefaultSourceRegistry() {
    }

    @Override
    public synchronized SourceRegistration register(Source source) {
        return register(null, source);
    }

    @Override
    public synchronized SourceRegistration register(
            SourceExtensionManifest manifest,
            Source source) {
        ensureOpen();
        Source value = Objects.requireNonNull(source, "source must not be null");
        SourceDescriptor descriptor = Objects.requireNonNull(
                value.descriptor(), "source descriptor must not be null");
        if (!SourceSdk.API_VERSION.supports(descriptor.requiredApiVersion())) {
            throw new SourceRegistrationException(
                    "Source " + descriptor.id() + " requires API " + descriptor.requiredApiVersion()
                            + " but Anilib provides " + SourceSdk.API_VERSION);
        }
        if (manifest != null && !manifest.sourceId().equals(descriptor.id())) {
            throw new SourceRegistrationException(
                    "Extension manifest source ID does not match descriptor: " + descriptor.id());
        }
        Entry entry = new Entry(value, Optional.ofNullable(manifest));
        if (sources.putIfAbsent(descriptor.id(), entry) != null) {
            throw new SourceRegistrationException("Duplicate source id: " + descriptor.id());
        }
        return new Registration(descriptor.id(), entry);
    }

    @Override
    public synchronized List<Source> sources() {
        ensureOpen();
        return sources.values().stream().map(Entry::source).toList();
    }

    @Override
    public synchronized List<InstalledSourceExtension> extensions() {
        ensureOpen();
        return sources.values().stream()
                .filter(entry -> entry.manifest().isPresent())
                .map(entry -> new InstalledSourceExtension(
                        entry.manifest().orElseThrow(),
                        entry.source().descriptor()))
                .toList();
    }

    @Override
    public synchronized Optional<Source> find(SourceId id) {
        ensureOpen();
        Entry entry = sources.get(Objects.requireNonNull(id, "id must not be null"));
        return entry == null ? Optional.empty() : Optional.of(entry.source());
    }

    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        closed = true;
        sources.clear();
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("Source registry is closed");
        }
    }

    private final class Registration implements SourceRegistration {
        private final SourceId id;
        private final Entry entry;
        private boolean removed;

        private Registration(SourceId id, Entry entry) {
            this.id = id;
            this.entry = entry;
        }

        @Override
        public SourceId sourceId() {
            return id;
        }

        @Override
        public void close() {
            synchronized (DefaultSourceRegistry.this) {
                if (!removed && !closed) {
                    sources.remove(id, entry);
                    removed = true;
                }
            }
        }
    }

    private record Entry(Source source, Optional<SourceExtensionManifest> manifest) {
        private Entry {
            Objects.requireNonNull(source, "source must not be null");
            Objects.requireNonNull(manifest, "manifest must not be null");
        }
    }
}
