package fr.vriege.anilib.feature.player.runtime;

import fr.vriege.anilib.feature.library.LibraryCatalog;
import fr.vriege.anilib.feature.library.LibraryHistoryEntry;
import fr.vriege.anilib.feature.library.LibraryItem;
import fr.vriege.anilib.feature.library.LibraryItemId;
import fr.vriege.anilib.feature.library.LibraryOrigin;
import fr.vriege.anilib.feature.library.LibraryProgress;
import fr.vriege.anilib.feature.library.MediaKind;
import fr.vriege.anilib.feature.player.EpisodeSnapshot;
import fr.vriege.anilib.feature.player.PlaybackState;
import fr.vriege.anilib.feature.player.PlayerBackend;
import fr.vriege.anilib.feature.player.PlayerBackends;
import fr.vriege.anilib.feature.player.PlayerException;
import fr.vriege.anilib.feature.player.PlayerService;
import fr.vriege.anilib.feature.player.PlayerSession;
import fr.vriege.anilib.feature.player.PlayerSessionSnapshot;
import fr.vriege.anilib.feature.source.Source;
import fr.vriege.anilib.feature.source.SourceCatalogueItemId;
import fr.vriege.anilib.feature.source.SourceEpisode;
import fr.vriege.anilib.feature.source.SourceEpisodeId;
import fr.vriege.anilib.feature.source.SourceId;
import fr.vriege.anilib.feature.source.SourceRegistry;
import fr.vriege.anilib.feature.source.SourceVideoStream;
import fr.vriege.anilib.feature.source.StreamingSource;

import java.nio.file.Path;
import java.time.Clock;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BooleanSupplier;

public final class DefaultPlayerService implements PlayerService, AutoCloseable {
    private static final int MAXIMUM_EPISODES = 100_000;
    private static final int MAXIMUM_STREAMS = 512;
    private final SourceRegistry sources;
    private final LibraryCatalog library;
    private final PlaybackStateStore states;
    private final PlayerBackend backend;
    private final Clock clock;
    private final BooleanSupplier persistenceAllowed;
    private final Set<DefaultPlayerSession> sessions = new HashSet<>();
    private final Set<Runnable> listeners = new HashSet<>();
    private boolean closed;

    public DefaultPlayerService(
            SourceRegistry sources,
            LibraryCatalog library,
            Path stateFile) {
        this(sources, library, stateFile, PlayerBackends.unavailable());
    }

    public DefaultPlayerService(
            SourceRegistry sources,
            LibraryCatalog library,
            Path stateFile,
            PlayerBackend backend) {
        this(sources, library, stateFile, backend, () -> true);
    }

    public DefaultPlayerService(
            SourceRegistry sources,
            LibraryCatalog library,
            Path stateFile,
            PlayerBackend backend,
            BooleanSupplier persistenceAllowed) {
        this(
                sources,
                library,
                new PlaybackStateStore(stateFile),
                Clock.systemUTC(),
                backend,
                persistenceAllowed);
    }

    DefaultPlayerService(
            SourceRegistry sources,
            LibraryCatalog library,
            PlaybackStateStore states,
            Clock clock,
            PlayerBackend backend,
            BooleanSupplier persistenceAllowed) {
        this.sources = Objects.requireNonNull(sources, "sources must not be null");
        this.library = Objects.requireNonNull(library, "library must not be null");
        this.states = Objects.requireNonNull(states, "states must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.backend = Objects.requireNonNull(backend, "backend must not be null");
        this.persistenceAllowed = Objects.requireNonNull(
                persistenceAllowed,
                "persistenceAllowed must not be null");
    }

    @Override
    public synchronized boolean canOpen(LibraryItemId libraryItemId) {
        Objects.requireNonNull(libraryItemId, "libraryItemId must not be null");
        ensureOpen();
        Optional<LibraryItem> item = library.find(libraryItemId);
        return item.filter(value -> value.kind() == MediaKind.ANIME)
                .flatMap(LibraryItem::origin)
                .map(DefaultPlayerService::sourceItemId)
                .flatMap(id -> sources.find(id.sourceId()))
                .filter(StreamingSource.class::isInstance)
                .isPresent();
    }

    @Override
    public synchronized List<EpisodeSnapshot> episodes(LibraryItemId libraryItemId) {
        ensureOpen();
        ResolvedTitle resolved = resolve(libraryItemId);
        return validatedEpisodes(resolved.source(), resolved.sourceItemId()).stream()
                .map(episode -> new EpisodeSnapshot(
                        episode,
                        states.find(libraryItemId, episode.id())))
                .toList();
    }

    @Override
    public synchronized PlayerSession open(
            LibraryItemId libraryItemId,
            SourceEpisodeId episodeId) {
        ensureOpen();
        Objects.requireNonNull(episodeId, "episodeId must not be null");
        ResolvedTitle resolved = resolve(libraryItemId);
        if (!episodeId.itemId().equals(resolved.sourceItemId())) {
            throw new PlayerException("Episode does not belong to the selected library title");
        }
        SourceEpisode episode = validatedEpisodes(resolved.source(), resolved.sourceItemId()).stream()
                .filter(candidate -> candidate.id().equals(episodeId))
                .findFirst()
                .orElseThrow(() -> new PlayerException("Episode is no longer available"));
        List<SourceVideoStream> streams = validatedStreams(resolved.source(), episodeId);
        PlaybackState playback = states.find(libraryItemId, episodeId).orElseGet(() -> new PlaybackState(
                libraryItemId,
                episodeId,
                0L,
                PlaybackState.UNKNOWN_DURATION,
                false,
                clock.instant()));
        LibraryItem item = resolved.item();
        if (persistenceAllowed.getAsBoolean()) {
            library.save(item.recordHistory(new LibraryHistoryEntry(
                    episodeId.value(),
                    clock.instant(),
                    playback.positionMillis())));
        }
        PlayerSessionSnapshot snapshot = new PlayerSessionSnapshot(
                libraryItemId,
                item.title(),
                episode,
                streams,
                streams.getFirst().id(),
                Optional.empty(),
                playback);
        DefaultPlayerSession session = new DefaultPlayerSession(this, backend, snapshot);
        sessions.add(session);
        return session;
    }

    @Override
    public synchronized AutoCloseable observe(Runnable listener) {
        ensureOpen();
        Runnable value = Objects.requireNonNull(listener, "listener must not be null");
        listeners.add(value);
        return () -> removeListener(value);
    }

    synchronized PlaybackState updatePlayback(
            PlaybackState previous,
            long positionMillis,
            long durationMillis,
            boolean completed) {
        ensureOpen();
        boolean finalCompleted = completed
                || previous.completed()
                || durationMillis > 0 && positionMillis >= durationMillis;
        PlaybackState replacement = new PlaybackState(
                previous.libraryItemId(),
                previous.episodeId(),
                positionMillis,
                durationMillis,
                finalCompleted,
                clock.instant());
        if (!persistenceAllowed.getAsBoolean()) {
            notifyListeners();
            return replacement;
        }
        Optional<PlaybackState> storedBefore = states.find(
                replacement.libraryItemId(),
                replacement.episodeId());
        LibraryItem itemBefore = library.find(replacement.libraryItemId())
                .orElseThrow(() -> new PlayerException("Library item was removed during playback"));
        states.save(replacement);
        try {
            library.save(itemBefore.withProgress(new LibraryProgress(
                    replacement.episodeId().value(),
                    replacement.positionMillis(),
                    replacement.durationMillis(),
                    replacement.updatedAt())));
        } catch (RuntimeException failure) {
            try {
                states.restore(replacement.libraryItemId(), replacement.episodeId(), storedBefore);
            } catch (RuntimeException rollbackFailure) {
                failure.addSuppressed(rollbackFailure);
            }
            throw new PlayerException("Unable to update playback state", failure);
        }
        notifyListeners();
        return replacement;
    }

    PlaybackStateStore stateStore() {
        return states;
    }

    public synchronized int cleanUnusedData() {
        ensureOpen();
        List<PlaybackState> current = states.snapshot();
        List<PlaybackState> retained = current.stream()
                .filter(state -> library.find(state.libraryItemId()).isPresent())
                .toList();
        int removed = current.size() - retained.size();
        if (removed > 0) {
            states.replaceAll(retained);
            notifyListeners();
        }
        return removed;
    }

    synchronized void removeSession(DefaultPlayerSession session) {
        sessions.remove(session);
    }

    private ResolvedTitle resolve(LibraryItemId libraryItemId) {
        Objects.requireNonNull(libraryItemId, "libraryItemId must not be null");
        LibraryItem item = library.find(libraryItemId)
                .orElseThrow(() -> new PlayerException("Library item was not found"));
        if (item.kind() != MediaKind.ANIME) {
            throw new PlayerException("Only anime titles expose episodes");
        }
        LibraryOrigin origin = item.origin()
                .orElseThrow(() -> new PlayerException("Library item has no source origin"));
        SourceCatalogueItemId sourceItemId = sourceItemId(origin);
        Source source = sources.find(sourceItemId.sourceId())
                .orElseThrow(() -> new PlayerException("Library source is not installed"));
        if (!(source instanceof StreamingSource streamingSource)) {
            throw new PlayerException("Library source does not provide streaming content");
        }
        return new ResolvedTitle(item, sourceItemId, streamingSource);
    }

    private static List<SourceEpisode> validatedEpisodes(
            StreamingSource source,
            SourceCatalogueItemId sourceItemId) {
        List<SourceEpisode> episodes = List.copyOf(Objects.requireNonNull(
                source.episodes(sourceItemId),
                "streaming source returned null episodes"));
        if (episodes.size() > MAXIMUM_EPISODES) {
            throw new PlayerException("Streaming source returned too many episodes");
        }
        Set<SourceEpisodeId> identities = new HashSet<>();
        for (SourceEpisode episode : episodes) {
            if (!episode.id().itemId().equals(sourceItemId)) {
                throw new PlayerException("Streaming source returned an episode for another title");
            }
            if (!identities.add(episode.id())) {
                throw new PlayerException("Streaming source returned a duplicate episode");
            }
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
            throw new PlayerException("Episode contains no playable streams");
        }
        if (streams.size() > MAXIMUM_STREAMS) {
            throw new PlayerException("Streaming source returned too many streams");
        }
        Set<String> identities = new HashSet<>();
        for (SourceVideoStream stream : streams) {
            if (!identities.add(stream.id())) {
                throw new PlayerException("Streaming source returned a duplicate stream");
            }
        }
        return streams;
    }

    private static SourceCatalogueItemId sourceItemId(LibraryOrigin origin) {
        return new SourceCatalogueItemId(SourceId.of(origin.sourceId()), origin.sourceItemKey());
    }

    private synchronized void removeListener(Runnable listener) {
        listeners.remove(listener);
    }

    private void notifyListeners() {
        List.copyOf(listeners).forEach(listener -> {
            try {
                listener.run();
            } catch (RuntimeException ignored) {
                // Observers cannot compromise playback persistence.
            }
        });
    }

    private void ensureOpen() {
        if (closed) {
            throw new PlayerException("Player service is closed");
        }
    }

    @Override
    public synchronized void close() {
        if (!closed) {
            closed = true;
            List.copyOf(sessions).forEach(DefaultPlayerSession::close);
            sessions.clear();
            listeners.clear();
        }
    }

    private record ResolvedTitle(
            LibraryItem item,
            SourceCatalogueItemId sourceItemId,
            StreamingSource source) {
    }
}
