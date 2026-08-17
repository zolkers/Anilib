package fr.vriege.anilib.feature.reader.runtime;

import fr.vriege.anilib.feature.library.LibraryCatalog;
import fr.vriege.anilib.feature.library.LibraryHistoryEntry;
import fr.vriege.anilib.feature.library.LibraryItem;
import fr.vriege.anilib.feature.library.LibraryItemId;
import fr.vriege.anilib.feature.library.LibraryOrigin;
import fr.vriege.anilib.feature.reader.ReaderException;
import fr.vriege.anilib.feature.reader.ReaderPolicy;
import fr.vriege.anilib.feature.reader.ReaderService;
import fr.vriege.anilib.feature.reader.ReaderSession;
import fr.vriege.anilib.feature.source.PagedSource;
import fr.vriege.anilib.feature.source.Source;
import fr.vriege.anilib.feature.source.SourceCatalogueItemId;
import fr.vriege.anilib.feature.source.SourceContentUnit;
import fr.vriege.anilib.feature.source.SourceId;
import fr.vriege.anilib.feature.source.SourcePageResource;
import fr.vriege.anilib.feature.source.SourceRegistry;

import java.time.Clock;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Shared reader engine resolving library origins into validated paged sessions. */
public final class DefaultReaderService implements ReaderService, AutoCloseable {
    private final SourceRegistry sources;
    private final LibraryCatalog library;
    private final ReaderPolicy policy;
    private final Clock clock;
    private final ExecutorService pageExecutor;
    private final Set<DefaultReaderSession> sessions = new HashSet<>();
    private boolean closed;

    public DefaultReaderService(SourceRegistry sources, LibraryCatalog library, ReaderPolicy policy) {
        this(sources, library, policy, Clock.systemUTC(), Executors.newFixedThreadPool(2));
    }

    DefaultReaderService(
            SourceRegistry sources,
            LibraryCatalog library,
            ReaderPolicy policy,
            Clock clock,
            ExecutorService pageExecutor) {
        this.sources = Objects.requireNonNull(sources, "sources must not be null");
        this.library = Objects.requireNonNull(library, "library must not be null");
        this.policy = Objects.requireNonNull(policy, "policy must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.pageExecutor = Objects.requireNonNull(pageExecutor, "pageExecutor must not be null");
    }

    @Override
    public synchronized boolean canOpen(LibraryItemId libraryItemId) {
        Objects.requireNonNull(libraryItemId, "libraryItemId must not be null");
        ensureOpen();
        return library.find(libraryItemId)
                .flatMap(LibraryItem::origin)
                .flatMap(origin -> sources.find(SourceId.of(origin.sourceId())))
                .filter(PagedSource.class::isInstance)
                .isPresent();
    }

    @Override
    public synchronized ReaderSession open(LibraryItemId libraryItemId) {
        Objects.requireNonNull(libraryItemId, "libraryItemId must not be null");
        ensureOpen();
        LibraryItem item = library.find(libraryItemId)
                .orElseThrow(() -> new ReaderException("Library item was not found"));
        LibraryOrigin origin = item.origin()
                .orElseThrow(() -> new ReaderException("Library item has no source origin"));
        Source source = sources.find(SourceId.of(origin.sourceId()))
                .orElseThrow(() -> new ReaderException("Library source is not installed"));
        if (!(source instanceof PagedSource pagedSource)) {
            throw new ReaderException("Library source does not provide paged content");
        }

        SourceCatalogueItemId sourceItemId = new SourceCatalogueItemId(
                source.descriptor().id(),
                origin.sourceItemKey());
        List<SourceContentUnit> units = validatedUnits(pagedSource, sourceItemId);
        SourceContentUnit unit = selectUnit(item, units);
        List<SourcePageResource> pages = validatedPages(pagedSource, unit);
        int initialPage = item.progress()
                .filter(progress -> progress.contentId().equals(unit.id().value()))
                .map(progress -> (int) Math.min(progress.position(), pages.size() - 1L))
                .orElse(0);

        library.save(item.recordHistory(new LibraryHistoryEntry(
                unit.id().value(),
                clock.instant(),
                initialPage)));
        ReaderPagePipeline pipeline = new ReaderPagePipeline(pagedSource, pages, policy, pageExecutor);
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
        pages.sort(java.util.Comparator.comparingInt(SourcePageResource::index));
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
