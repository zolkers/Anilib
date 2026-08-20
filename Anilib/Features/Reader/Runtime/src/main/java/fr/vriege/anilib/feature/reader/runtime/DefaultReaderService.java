package fr.vriege.anilib.feature.reader.runtime;

import fr.vriege.anilib.feature.library.LibraryCatalog;
import fr.vriege.anilib.feature.library.LibraryHistoryEntry;
import fr.vriege.anilib.feature.library.LibraryItem;
import fr.vriege.anilib.feature.library.LibraryItemId;
import fr.vriege.anilib.feature.library.LibraryOrigin;
import fr.vriege.anilib.feature.reader.ReaderException;
import fr.vriege.anilib.feature.reader.ReaderContent;
import fr.vriege.anilib.feature.reader.ReaderContentProvider;
import fr.vriege.anilib.feature.reader.ReaderContentRegistrar;
import fr.vriege.anilib.feature.reader.ReaderPolicy;
import fr.vriege.anilib.feature.reader.ReaderService;
import fr.vriege.anilib.feature.reader.ReaderSession;
import fr.vriege.anilib.feature.source.PagedSource;
import fr.vriege.anilib.feature.source.Source;
import fr.vriege.anilib.feature.source.SourceCatalogueItemId;
import fr.vriege.anilib.feature.source.SourceContentUnit;
import fr.vriege.anilib.feature.source.SourceContentUnitId;
import fr.vriege.anilib.feature.source.SourceId;
import fr.vriege.anilib.feature.source.SourcePageResource;
import fr.vriege.anilib.feature.source.SourceRegistry;

import java.time.Clock;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BooleanSupplier;
import java.util.Comparator;
import java.util.UUID;
import java.util.function.Function;

public final class DefaultReaderService implements ReaderService, ReaderContentRegistrar, AutoCloseable {
    private final SourceRegistry sources;
    private final LibraryCatalog library;
    private final ReaderPolicy policy;
    private final Clock clock;
    private final ExecutorService pageExecutor;
    private final BooleanSupplier persistenceAllowed;
    private final Set<DefaultReaderSession> sessions = new HashSet<>();
    private ReaderContentProvider contentProvider;
    private boolean closed;

    public DefaultReaderService(SourceRegistry sources, LibraryCatalog library, ReaderPolicy policy) {
        this(sources, library, policy, () -> true);
    }

    public DefaultReaderService(
            SourceRegistry sources,
            LibraryCatalog library,
            ReaderPolicy policy,
            BooleanSupplier persistenceAllowed) {
        this(
                sources,
                library,
                policy,
                Clock.systemUTC(),
                Executors.newFixedThreadPool(2),
                persistenceAllowed);
    }

    DefaultReaderService(
            SourceRegistry sources,
            LibraryCatalog library,
            ReaderPolicy policy,
            Clock clock,
            ExecutorService pageExecutor,
            BooleanSupplier persistenceAllowed) {
        this.sources = Objects.requireNonNull(sources, "sources must not be null");
        this.library = Objects.requireNonNull(library, "library must not be null");
        this.policy = Objects.requireNonNull(policy, "policy must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.pageExecutor = Objects.requireNonNull(pageExecutor, "pageExecutor must not be null");
        this.persistenceAllowed = Objects.requireNonNull(
                persistenceAllowed,
                "persistenceAllowed must not be null");
    }

    @Override
    public synchronized boolean canOpen(LibraryItemId libraryItemId) {
        Objects.requireNonNull(libraryItemId, "libraryItemId must not be null");
        ensureOpen();
        Optional<LibraryOrigin> origin = library.find(libraryItemId).flatMap(LibraryItem::origin);
        if (origin.isEmpty()) {
            return false;
        }
        SourceCatalogueItemId itemId = sourceItemId(origin.orElseThrow());
        if (contentProvider != null
                && contentProvider.find(itemId, preferredContentId(libraryItemId)).isPresent()) {
            return true;
        }
        return fallbackAllowed()
                && sources.find(itemId.sourceId()).filter(PagedSource.class::isInstance).isPresent();
    }

    @Override
    public synchronized List<SourceContentUnit> contentUnits(LibraryItemId libraryItemId) {
        Objects.requireNonNull(libraryItemId, "libraryItemId must not be null");
        ensureOpen();
        LibraryItem item = library.find(libraryItemId)
                .orElseThrow(() -> new ReaderException("Library item was not found"));
        LibraryOrigin origin = item.origin()
                .orElseThrow(() -> new ReaderException("Library item has no source origin"));
        SourceCatalogueItemId itemId = sourceItemId(origin);
        Source source = sources.find(itemId.sourceId())
                .orElseThrow(() -> new ReaderException("Library source is not installed"));
        if (!(source instanceof PagedSource pagedSource)) {
            throw new ReaderException("Library source does not provide paged content");
        }
        return validatedUnits(pagedSource, itemId);
    }

    @Override
    public synchronized List<SourceContentUnit> contentUnits(SourceCatalogueItemId itemId) {
        ensureOpen();
        PagedSource source = pagedSource(itemId);
        return validatedUnits(source, itemId);
    }

    @Override
    public synchronized ReaderSession open(LibraryItemId libraryItemId) {
        return openSelected(libraryItemId, null);
    }

    @Override
    public synchronized ReaderSession open(
            LibraryItemId libraryItemId,
            SourceContentUnitId contentUnitId) {
        return openSelected(
                libraryItemId,
                Objects.requireNonNull(contentUnitId, "contentUnitId must not be null"));
    }

    @Override
    public synchronized ReaderSession open(String title, SourceContentUnitId contentUnitId) {
        ensureOpen();
        String selectedTitle = Objects.requireNonNull(title, "title must not be null");
        SourceContentUnitId selectedId = Objects.requireNonNull(
                contentUnitId, "contentUnitId must not be null");
        PagedSource source = pagedSource(selectedId.itemId());
        SourceContentUnit unit = validatedUnits(source, selectedId.itemId()).stream()
                .filter(candidate -> candidate.id().equals(selectedId))
                .findFirst()
                .orElseThrow(() -> new ReaderException("Requested content unit was not found for this title"));
        List<SourcePageResource> pages = validatedPages(source, unit);
        ReaderPagePipeline pipeline = new ReaderPagePipeline(source::readPage, pages, policy, pageExecutor);
        LibraryItemId transientId = new LibraryItemId("transient-reader-" + UUID.randomUUID());
        DefaultReaderSession[] holder = new DefaultReaderSession[1];
        DefaultReaderSession session = new DefaultReaderSession(
                library,
                transientId,
                selectedTitle,
                unit,
                pages.size(),
                0,
                pipeline,
                clock,
                () -> false,
                () -> removeSession(holder[0]));
        holder[0] = session;
        sessions.add(session);
        return session;
    }

    private ReaderSession openSelected(
            LibraryItemId libraryItemId,
            SourceContentUnitId requestedContentUnitId) {
        Objects.requireNonNull(libraryItemId, "libraryItemId must not be null");
        ensureOpen();
        LibraryItem item = library.find(libraryItemId)
                .orElseThrow(() -> new ReaderException("Library item was not found"));
        LibraryOrigin origin = item.origin()
                .orElseThrow(() -> new ReaderException("Library item has no source origin"));
        SourceCatalogueItemId sourceItemId = sourceItemId(origin);
        ReaderContentProvider provider = contentProvider;
        Optional<String> preferredContentId = requestedContentUnitId == null
                ? item.progress().map(progress -> progress.contentId())
                : Optional.of(requestedContentUnitId.value());
        Optional<ReaderContent> alternate = provider == null
                ? Optional.empty()
                : provider.find(sourceItemId, preferredContentId);
        SourceContentUnit unit;
        List<SourcePageResource> pages;
        Function<SourcePageResource, byte[]> pageReader;
        if (alternate.isPresent()) {
            ReaderContent content = alternate.orElseThrow();
            unit = content.contentUnit();
            if (requestedContentUnitId != null && !unit.id().equals(requestedContentUnitId)) {
                throw new ReaderException("Requested content unit is not available offline");
            }
            pages = content.pages();
            pageReader = provider::readPage;
        } else {
            if (!fallbackAllowed()) {
                throw new ReaderException("This title is not available while offline mode is enabled");
            }
            Source source = sources.find(sourceItemId.sourceId())
                    .orElseThrow(() -> new ReaderException("Library source is not installed"));
            if (!(source instanceof PagedSource pagedSource)) {
                throw new ReaderException("Library source does not provide paged content");
            }
            List<SourceContentUnit> units = validatedUnits(pagedSource, sourceItemId);
            unit = requestedContentUnitId == null
                    ? selectUnit(item, units)
                    : units.stream()
                            .filter(candidate -> candidate.id().equals(requestedContentUnitId))
                            .findFirst()
                            .orElseThrow(() -> new ReaderException(
                                    "Requested content unit was not found for this title"));
            pages = validatedPages(pagedSource, unit);
            pageReader = pagedSource::readPage;
        }
        int initialPage = item.progress()
                .filter(progress -> progress.contentId().equals(unit.id().value()))
                .map(progress -> (int) Math.min(progress.position(), pages.size() - 1L))
                .orElse(0);

        if (persistenceAllowed.getAsBoolean()) {
            library.save(item.recordHistory(new LibraryHistoryEntry(
                    unit.id().value(),
                    clock.instant(),
                    initialPage)));
        }
        ReaderPagePipeline pipeline = new ReaderPagePipeline(pageReader, pages, policy, pageExecutor);
        DefaultReaderSession[] holder = new DefaultReaderSession[1];
        DefaultReaderSession session = new DefaultReaderSession(
                library,
                item.id(),
                item.title(),
                unit,
                pages.size(),
                initialPage,
                pipeline,
                clock,
                persistenceAllowed,
                () -> removeSession(holder[0]));
        holder[0] = session;
        sessions.add(session);
        return session;
    }

    private List<SourceContentUnit> validatedUnits(PagedSource source, SourceCatalogueItemId itemId) {
        List<SourceContentUnit> units = List.copyOf(Objects.requireNonNull(
                source.contentUnits(itemId),
                "paged source returned null content units"));
        if (units.isEmpty()) {
            throw new ReaderException("No readable content is available for this title");
        }
        Set<Object> identities = new HashSet<>();
        for (SourceContentUnit unit : units) {
            if (!unit.id().itemId().equals(itemId)) {
                throw new ReaderException("Paged source returned a content unit for another title");
            }
            if (!identities.add(unit.id())) {
                throw new ReaderException("Paged source returned a duplicate content unit");
            }
        }
        return units;
    }

    private PagedSource pagedSource(SourceCatalogueItemId itemId) {
        Objects.requireNonNull(itemId, "itemId must not be null");
        Source source = sources.find(itemId.sourceId())
                .orElseThrow(() -> new ReaderException("Source is not installed"));
        if (!(source instanceof PagedSource pagedSource)) {
            throw new ReaderException("Source does not provide paged content");
        }
        return pagedSource;
    }

    private SourceContentUnit selectUnit(LibraryItem item, List<SourceContentUnit> units) {
        if (item.progress().isPresent()) {
            String contentId = item.progress().orElseThrow().contentId();
            for (SourceContentUnit unit : units) {
                if (unit.id().value().equals(contentId)) {
                    return unit;
                }
            }
        }
        return units.get(units.size() - 1);
    }

    private List<SourcePageResource> validatedPages(PagedSource source, SourceContentUnit unit) {
        List<SourcePageResource> pages = new ArrayList<>(Objects.requireNonNull(
                source.pages(unit.id()),
                "paged source returned null pages"));
        if (pages.isEmpty()) {
            throw new ReaderException("The selected content unit contains no pages");
        }
        pages.sort(Comparator.comparingInt(SourcePageResource::index));
        for (int index = 0; index < pages.size(); index++) {
            SourcePageResource page = pages.get(index);
            if (!page.contentUnitId().equals(unit.id()) || page.index() != index) {
                throw new ReaderException("Paged source returned an invalid page sequence");
            }
        }
        return List.copyOf(pages);
    }

    private synchronized void removeSession(DefaultReaderSession session) {
        sessions.remove(session);
    }

    @Override
    public synchronized AutoCloseable register(ReaderContentProvider provider) {
        Objects.requireNonNull(provider, "provider must not be null");
        ensureOpen();
        if (contentProvider != null) {
            throw new ReaderException("A Reader content provider is already registered");
        }
        contentProvider = provider;
        return () -> unregister(provider);
    }

    private synchronized void unregister(ReaderContentProvider provider) {
        if (contentProvider == provider) {
            contentProvider = null;
        }
    }

    private Optional<String> preferredContentId(LibraryItemId libraryItemId) {
        return library.find(libraryItemId)
                .flatMap(LibraryItem::progress)
                .map(progress -> progress.contentId());
    }

    private boolean fallbackAllowed() {
        return contentProvider == null || contentProvider.sourceFallbackAllowed();
    }

    private static SourceCatalogueItemId sourceItemId(LibraryOrigin origin) {
        return new SourceCatalogueItemId(SourceId.of(origin.sourceId()), origin.sourceItemKey());
    }

    private void ensureOpen() {
        if (closed) {
            throw new ReaderException("Reader service is closed");
        }
    }

    @Override
    public synchronized void close() {
        if (!closed) {
            closed = true;
            List.copyOf(sessions).forEach(DefaultReaderSession::close);
            sessions.clear();
            pageExecutor.shutdownNow();
        }
    }
}
