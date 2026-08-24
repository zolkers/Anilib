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
import fr.vriege.anilib.feature.player.PlayerProgressEvent;
import fr.vriege.anilib.feature.player.PlayerContentProvider;
import fr.vriege.anilib.feature.player.PlayerContentRegistrar;
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
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.UUID;
import java.util.stream.Collectors;

public final class DefaultPlayerService implements PlayerService, PlayerContentRegistrar, AutoCloseable {
    private static final int MAXIMUM_EPISODES = 100_000;
    private static final int MAXIMUM_STREAMS = 512;
    private static final Duration EPISODE_CACHE_TTL = Duration.ofMinutes(10L);
    private final SourceRegistry sources;
    private final LibraryCatalog library;
    private final PlaybackStateStore states;
    private final PlayerBackend backend;
    private final Clock clock;
    private final BooleanSupplier persistenceAllowed;
    private final Set<DefaultPlayerSession> sessions = new HashSet<>();
    private final Set<Runnable> listeners = new HashSet<>();
    private final Set<Consumer<PlayerProgressEvent>> progressListeners = new HashSet<>();
    private final Map<SourceCatalogueItemId, CachedEpisodes> episodeCache = new HashMap<>();
    private PlayerContentProvider contentProvider;
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
        if (item.filter(value -> value.kind() == MediaKind.ANIME).isEmpty()) {
            return false;
        }
        Optional<SourceCatalogueItemId> sourceItemId = item.orElseThrow().origin()
                .map(DefaultPlayerService::sourceItemId);
        if (sourceItemId.isEmpty()) {
            return false;
        }
        PlayerContentProvider provider = contentProvider;
        if (provider != null && !provider.episodes(sourceItemId.orElseThrow()).isEmpty()) {
            return true;
        }
        return fallbackAllowed()
                && sources.find(sourceItemId.orElseThrow().sourceId())
                        .filter(StreamingSource.class::isInstance)
                        .isPresent();
    }

    @Override
    public synchronized List<EpisodeSnapshot> episodes(LibraryItemId libraryItemId) {
        ensureOpen();
        ResolvedLibrary resolved = resolveLibrary(libraryItemId);
        return episodeSnapshots(libraryItemId, availableEpisodes(resolved));
    }

    @Override
    public synchronized List<EpisodeSnapshot> episodes(SourceCatalogueItemId itemId) {
        ensureOpen();
        StreamingSource source = streamingSource(itemId);
        Optional<LibraryItemId> libraryItemId = library.snapshot().stream()
                .filter(item -> item.origin()
                        .filter(origin -> origin.equals(new LibraryOrigin(
                                itemId.sourceId().toString(), itemId.value())))
                        .isPresent())
                .map(LibraryItem::id)
                .findFirst();
        return episodes(source, itemId).stream()
                .map(episode -> new EpisodeSnapshot(
                        episode,
                        libraryItemId.flatMap(id -> states.find(id, episode.id()))))
                .toList();
    }

    @Override
    public synchronized List<EpisodeSnapshot> setEpisodesCompleted(
            LibraryItemId libraryItemId,
            Collection<SourceEpisodeId> episodeIds,
            boolean completed) {
        ensureOpen();
        LibraryItemId itemId = Objects.requireNonNull(libraryItemId, "libraryItemId must not be null");
        Set<SourceEpisodeId> selected = Set.copyOf(Objects.requireNonNull(
                episodeIds,
                "episodeIds must not be null"));
        ResolvedLibrary resolved = resolveLibrary(itemId);
        List<SourceEpisode> available = availableEpisodes(resolved);
        Map<SourceEpisodeId, SourceEpisode> indexed = new LinkedHashMap<>();
        available.forEach(episode -> indexed.put(episode.id(), episode));
        if (!indexed.keySet().containsAll(selected)) {
            throw new PlayerException("One or more episodes no longer belong to this title");
        }
        if (selected.isEmpty() || !persistenceAllowed.getAsBoolean()) {
            return episodeSnapshots(itemId, available);
        }

        List<PlaybackState> replacement = states.snapshot().stream()
                .filter(state -> !state.libraryItemId().equals(itemId) || !selected.contains(state.episodeId()))
                .collect(Collectors.toCollection(ArrayList::new));
        Instant updatedAt = clock.instant();
        PlayerProgressEvent highestProgress = null;
        for (SourceEpisodeId episodeId : selected) {
            Optional<PlaybackState> previous = states.find(itemId, episodeId);
            if (!completed && previous.isEmpty()) {
                continue;
            }
            PlaybackState before = previous.orElseGet(() -> new PlaybackState(
                    itemId,
                    episodeId,
                    0L,
                    PlaybackState.UNKNOWN_DURATION,
                    false,
                    updatedAt));
            replacement.add(new PlaybackState(
                    itemId,
                    episodeId,
                    before.positionMillis(),
                    before.durationMillis(),
                    completed,
                    updatedAt));
            SourceEpisode episode = indexed.get(episodeId);
            if (completed && episode.episodeNumber() >= 0.0d
                    && (highestProgress == null
                    || episode.episodeNumber() > highestProgress.episodeNumber())) {
                highestProgress = new PlayerProgressEvent(itemId, episodeId, episode.episodeNumber());
            }
        }
        states.replaceAll(replacement);
        notifyListeners();
        if (highestProgress != null) {
            notifyProgress(highestProgress);
        }
        return episodeSnapshots(itemId, available);
    }

    @Override
    public synchronized PlayerSession open(
            LibraryItemId libraryItemId,
            SourceEpisodeId episodeId) {
        ensureOpen();
        Objects.requireNonNull(episodeId, "episodeId must not be null");
        ResolvedLibrary resolved = resolveLibrary(libraryItemId);
        if (!episodeId.itemId().equals(resolved.sourceItemId())) {
            throw new PlayerException("Episode does not belong to the selected library title");
        }
        SourceEpisode episode = episodeForOpen(resolved, episodeId);
        ResolvedStreams resolvedStreams = availableStreams(resolved, episodeId);
        List<SourceVideoStream> streams = resolvedStreams.initial();
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
        DefaultPlayerSession session = new DefaultPlayerSession(
                this,
                backend,
                snapshot,
                resolvedStreams.onlineLoader());
        sessions.add(session);
        return session;
    }

    @Override
    public synchronized PlayerSession open(String title, SourceEpisodeId episodeId) {
        ensureOpen();
        String selectedTitle = Objects.requireNonNull(title, "title must not be null");
        SourceEpisodeId selectedEpisodeId = Objects.requireNonNull(
                episodeId, "episodeId must not be null");
        StreamingSource source = streamingSource(selectedEpisodeId.itemId());
        SourceEpisode episode = episodes(source, selectedEpisodeId.itemId()).stream()
                .filter(candidate -> candidate.id().equals(selectedEpisodeId))
                .findFirst()
                .orElseThrow(() -> new PlayerException("Episode is no longer available"));
        List<SourceVideoStream> streams = validatedStreams(source, selectedEpisodeId);
        LibraryItemId transientId = new LibraryItemId("transient-player-" + UUID.randomUUID());
        PlaybackState playback = new PlaybackState(
                transientId, selectedEpisodeId, 0L, PlaybackState.UNKNOWN_DURATION, false, clock.instant());
        PlayerSessionSnapshot snapshot = new PlayerSessionSnapshot(
                transientId,
                selectedTitle,
                episode,
                streams,
                streams.getFirst().id(),
                Optional.empty(),
                playback);
        DefaultPlayerSession session = new DefaultPlayerSession(this, backend, snapshot, null);
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

    @Override
    public synchronized AutoCloseable observeProgress(Consumer<PlayerProgressEvent> listener) {
        ensureOpen();
        Consumer<PlayerProgressEvent> value = Objects.requireNonNull(listener, "listener must not be null");
        progressListeners.add(value);
        return () -> removeProgressListener(value);
    }

    @Override
    public synchronized AutoCloseable register(PlayerContentProvider provider) {
        Objects.requireNonNull(provider, "provider must not be null");
        ensureOpen();
        if (contentProvider != null) {
            throw new PlayerException("A Player content provider is already registered");
        }
        contentProvider = provider;
        return () -> unregister(provider);
    }

    synchronized PlaybackState updatePlayback(
            PlaybackState previous,
            double episodeNumber,
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
        if (library.find(previous.libraryItemId()).isEmpty()) {
            notifyListeners();
            return replacement;
        }
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
            library.save(itemBefore
                    .withProgress(new LibraryProgress(
                            replacement.episodeId().value(),
                            replacement.positionMillis(),
                            replacement.durationMillis(),
                            replacement.updatedAt()))
                    .recordHistory(new LibraryHistoryEntry(
                            replacement.episodeId().value(),
                            replacement.updatedAt(),
                            replacement.positionMillis())));
        } catch (RuntimeException failure) {
            try {
                states.restore(replacement.libraryItemId(), replacement.episodeId(), storedBefore);
            } catch (RuntimeException rollbackFailure) {
                failure.addSuppressed(rollbackFailure);
            }
            throw new PlayerException("Unable to update playback state", failure);
        }
        notifyListeners();
        if (!previous.completed() && replacement.completed() && episodeNumber >= 0.0d) {
            notifyProgress(new PlayerProgressEvent(
                    replacement.libraryItemId(),
                    replacement.episodeId(),
                    episodeNumber));
        }
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

    private ResolvedLibrary resolveLibrary(LibraryItemId libraryItemId) {
        Objects.requireNonNull(libraryItemId, "libraryItemId must not be null");
        LibraryItem item = library.find(libraryItemId)
                .orElseThrow(() -> new PlayerException("Library item was not found"));
        if (item.kind() != MediaKind.ANIME) {
            throw new PlayerException("Only anime titles expose episodes");
        }
        LibraryOrigin origin = item.origin()
                .orElseThrow(() -> new PlayerException("Library item has no source origin"));
        SourceCatalogueItemId sourceItemId = sourceItemId(origin);
        return new ResolvedLibrary(item, sourceItemId);
    }

    private List<SourceEpisode> availableEpisodes(ResolvedLibrary resolved) {
        PlayerContentProvider provider = contentProvider;
        List<SourceEpisode> offline = provider == null
                ? List.of()
                : validatedProviderEpisodes(provider.episodes(resolved.sourceItemId()), resolved.sourceItemId());
        if (!fallbackAllowed()) {
            if (offline.isEmpty()) {
                throw new PlayerException("This title is not available while offline mode is enabled");
            }
            return offline;
        }
        Optional<Source> source = sources.find(resolved.sourceItemId().sourceId());
        if (source.orElse(null) instanceof StreamingSource streamingSource) {
            try {
                List<SourceEpisode> online = episodes(streamingSource, resolved.sourceItemId());
                if (offline.isEmpty()) {
                    return online;
                }
                Map<SourceEpisodeId, SourceEpisode> combined = new LinkedHashMap<>();
                online.forEach(episode -> combined.put(episode.id(), episode));
                offline.forEach(episode -> combined.putIfAbsent(episode.id(), episode));
                return List.copyOf(combined.values());
            } catch (RuntimeException failure) {
                if (offline.isEmpty()) {
                    throw failure;
                }
            }
        }
        if (!offline.isEmpty()) {
            return offline;
        }
        throw new PlayerException("Library source is not installed");
    }

    private SourceEpisode episodeForOpen(
            ResolvedLibrary resolved,
            SourceEpisodeId episodeId) {
        PlayerContentProvider provider = contentProvider;
        if (provider != null) {
            Optional<SourceEpisode> offline = validatedProviderEpisodes(
                    provider.episodes(resolved.sourceItemId()),
                    resolved.sourceItemId()).stream()
                    .filter(candidate -> candidate.id().equals(episodeId))
                    .findFirst();
            if (offline.isPresent()) {
                return offline.orElseThrow();
            }
        }
        return availableEpisodes(resolved).stream()
                .filter(candidate -> candidate.id().equals(episodeId))
                .findFirst()
                .orElseThrow(() -> new PlayerException("Episode is no longer available"));
    }

    private ResolvedStreams availableStreams(
            ResolvedLibrary resolved,
            SourceEpisodeId episodeId) {
        PlayerContentProvider provider = contentProvider;
        List<SourceVideoStream> offline = provider == null
                ? List.of()
                : validatedProviderStreams(provider.streams(episodeId));
        if (!fallbackAllowed()) {
            if (offline.isEmpty()) {
                throw new PlayerException("This episode is not available while offline mode is enabled");
            }
            return new ResolvedStreams(offline, null);
        }
        Optional<Source> installed = sources.find(resolved.sourceItemId().sourceId());
        if (installed.isEmpty()) {
            if (!offline.isEmpty()) {
                return new ResolvedStreams(offline, null);
            }
            throw new PlayerException("Library source is not installed");
        }
        Source source = installed.orElseThrow();
        if (!(source instanceof StreamingSource streamingSource)) {
            if (!offline.isEmpty()) {
                return new ResolvedStreams(offline, null);
            }
            throw new PlayerException("Library source does not provide streaming content");
        }
        if (!offline.isEmpty()) {
            Supplier<List<SourceVideoStream>> onlineLoader = () -> loadOnlineStreams(
                    resolved.sourceItemId(),
                    episodeId);
            return new ResolvedStreams(offline, onlineLoader);
        }
        return new ResolvedStreams(validatedStreams(streamingSource, episodeId), null);
    }

    private synchronized List<SourceVideoStream> loadOnlineStreams(
            SourceCatalogueItemId itemId,
            SourceEpisodeId episodeId) {
        ensureOpen();
        if (!fallbackAllowed()) {
            throw new PlayerException("Online playback is disabled while offline mode is enabled");
        }
        Source source = sources.find(itemId.sourceId())
                .orElseThrow(() -> new PlayerException("Library source is not installed"));
        if (!(source instanceof StreamingSource streamingSource)) {
            throw new PlayerException("Library source does not provide streaming content");
        }
        return validatedStreams(streamingSource, episodeId);
    }

    private static List<SourceEpisode> validatedProviderEpisodes(
            List<SourceEpisode> supplied,
            SourceCatalogueItemId itemId) {
        List<SourceEpisode> episodes = List.copyOf(Objects.requireNonNull(
                supplied,
                "player content provider returned null episodes"));
        if (episodes.size() > MAXIMUM_EPISODES
                || episodes.stream().anyMatch(episode -> !episode.id().itemId().equals(itemId))) {
            throw new PlayerException("Player content provider returned invalid episodes");
        }
        return episodes;
    }

    private static List<SourceVideoStream> validatedProviderStreams(List<SourceVideoStream> supplied) {
        List<SourceVideoStream> streams = List.copyOf(Objects.requireNonNull(
                supplied,
                "player content provider returned null streams"));
        if (streams.size() > MAXIMUM_STREAMS) {
            throw new PlayerException("Player content provider returned too many streams");
        }
        Set<String> identities = new HashSet<>();
        if (streams.stream().anyMatch(stream -> !identities.add(stream.id()))) {
            throw new PlayerException("Player content provider returned duplicate streams");
        }
        return streams;
    }

    private boolean fallbackAllowed() {
        return contentProvider == null || contentProvider.sourceFallbackAllowed();
    }

    private synchronized void unregister(PlayerContentProvider provider) {
        if (contentProvider == provider) {
            contentProvider = null;
        }
    }

    private StreamingSource streamingSource(SourceCatalogueItemId itemId) {
        Objects.requireNonNull(itemId, "itemId must not be null");
        Source source = sources.find(itemId.sourceId())
                .orElseThrow(() -> new PlayerException("Source is not installed"));
        if (!(source instanceof StreamingSource streamingSource)) {
            throw new PlayerException("Source does not provide streaming content");
        }
        return streamingSource;
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

    private synchronized void removeProgressListener(Consumer<PlayerProgressEvent> listener) {
        progressListeners.remove(listener);
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

    private void notifyProgress(PlayerProgressEvent event) {
        List.copyOf(progressListeners).forEach(listener -> {
            try {
                listener.accept(event);
            } catch (RuntimeException ignored) {
                // Progress observers cannot compromise local playback persistence.
            }
        });
    }

    private List<SourceEpisode> episodes(StreamingSource source, SourceCatalogueItemId sourceItemId) {
        CachedEpisodes cached = episodeCache.get(sourceItemId);
        Instant now = clock.instant();
        if (cached != null && cached.source() == source
                && now.isBefore(cached.loadedAt().plus(EPISODE_CACHE_TTL))) {
            return cached.episodes();
        }
        List<SourceEpisode> loaded = validatedEpisodes(source, sourceItemId);
        episodeCache.put(sourceItemId, new CachedEpisodes(source, loaded, now));
        return loaded;
    }

    private List<EpisodeSnapshot> episodeSnapshots(
            LibraryItemId libraryItemId,
            List<SourceEpisode> episodes) {
        return episodes.stream()
                .map(episode -> new EpisodeSnapshot(
                        episode,
                        states.find(libraryItemId, episode.id())))
                .toList();
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
            progressListeners.clear();
            episodeCache.clear();
            contentProvider = null;
        }
    }

    private record CachedEpisodes(
            StreamingSource source,
            List<SourceEpisode> episodes,
            Instant loadedAt) {
    }

    private record ResolvedLibrary(
            LibraryItem item,
            SourceCatalogueItemId sourceItemId) {
    }

    private record ResolvedStreams(
            List<SourceVideoStream> initial,
            Supplier<List<SourceVideoStream>> onlineLoader) {
    }
}
