package fr.vriege.anilib.feature.source.runtime;

import fr.vriege.anilib.feature.source.Source;
import fr.vriege.anilib.feature.source.SourceDescriptor;
import fr.vriege.anilib.feature.source.SourceId;
import fr.vriege.anilib.feature.source.SourceRegistrar;
import fr.vriege.anilib.feature.source.SourceRegistration;
import fr.vriege.anilib.feature.source.SourceRegistrationException;
import fr.vriege.anilib.feature.source.SourceRegistry;
import fr.vriege.anilib.feature.source.SourceSdk;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

/** Thread-safe Source registry with compatibility and duplicate checks. */
public final class DefaultSourceRegistry implements SourceRegistry, SourceRegistrar, AutoCloseable {
    private final TreeMap<SourceId, Source> sources = new TreeMap<>();
    private boolean closed;

    public DefaultSourceRegistry() {
    }

    @Override
    public synchronized SourceRegistration register(Source source) {
        ensureOpen();
        Source value = Objects.requireNonNull(source, "source must not be null");
        SourceDescriptor descriptor = Objects.requireNonNull(
                value.descriptor(), "source descriptor must not be null");
        if (!SourceSdk.API_VERSION.supports(descriptor.requiredApiVersion())) {
            throw new SourceRegistrationException(
                    "Source " + descriptor.id() + " requires API " + descriptor.requiredApiVersion()
                            + " but Anilib provides " + SourceSdk.API_VERSION);
        }
        if (sources.putIfAbsent(descriptor.id(), value) != null) {
            throw new SourceRegistrationException("Duplicate source id: " + descriptor.id());
        }
        return new Registration(descriptor.id(), value);
    }

    @Override
    public synchronized List<Source> sources() {
        ensureOpen();
        return List.copyOf(sources.values());
    }

    @Override
    public synchronized Optional<Source> find(SourceId id) {
        ensureOpen();
        return Optional.ofNullable(sources.get(Objects.requireNonNull(id, "id must not be null")));
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
        private final Source source;
        private boolean removed;

        private Registration(SourceId id, Source source) {
            this.id = id;
            this.source = source;
        }

        @Override
        public SourceId sourceId() {
            return id;
        }

        @Override
        public void close() {
            synchronized (DefaultSourceRegistry.this) {
                if (!removed && !closed) {
                    sources.remove(id, source);
                    removed = true;
                }
            }
        }
    }
}
