package fr.vriege.anilib.feature.reader.runtime;

import fr.vriege.anilib.feature.library.LibraryCatalog;
import fr.vriege.anilib.feature.library.LibraryItem;
import fr.vriege.anilib.feature.library.LibraryItemId;
import fr.vriege.anilib.feature.library.LibraryProgress;
import fr.vriege.anilib.feature.reader.ReaderException;
import fr.vriege.anilib.feature.reader.ReaderSession;
import fr.vriege.anilib.feature.reader.ReaderSessionSnapshot;
import fr.vriege.anilib.feature.reader.ReadingDirection;
import fr.vriege.anilib.feature.source.SourceContentUnit;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.function.BooleanSupplier;

final class DefaultReaderSession implements ReaderSession {
    private final LibraryCatalog library;
    private final LibraryItemId libraryItemId;
    private final String title;
    private final SourceContentUnit contentUnit;
    private final int pageCount;
    private final ReaderPagePipeline pipeline;
    private final Clock clock;
    private final Runnable onClose;
    private final BooleanSupplier persistenceAllowed;
    private int currentPageIndex;
    private ReadingDirection direction = ReadingDirection.LEFT_TO_RIGHT;
    private boolean closed;

    DefaultReaderSession(
            LibraryCatalog library,
            LibraryItemId libraryItemId,
            String title,
            SourceContentUnit contentUnit,
            int pageCount,
            int currentPageIndex,
            ReaderPagePipeline pipeline,
            Clock clock,
            BooleanSupplier persistenceAllowed,
            Runnable onClose) {
        this.library = Objects.requireNonNull(library, "library must not be null");
        this.libraryItemId = Objects.requireNonNull(libraryItemId, "libraryItemId must not be null");
        this.title = Objects.requireNonNull(title, "title must not be null");
        this.contentUnit = Objects.requireNonNull(contentUnit, "contentUnit must not be null");
        this.pageCount = pageCount;
        this.currentPageIndex = currentPageIndex;
        this.pipeline = Objects.requireNonNull(pipeline, "pipeline must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.persistenceAllowed = Objects.requireNonNull(
                persistenceAllowed,
                "persistenceAllowed must not be null");
        this.onClose = Objects.requireNonNull(onClose, "onClose must not be null");
    }

    @Override
    public synchronized ReaderSessionSnapshot snapshot() {
        ensureOpen();
        return new ReaderSessionSnapshot(
                libraryItemId,
                title,
                contentUnit,
                currentPageIndex,
                pageCount,
                direction);
    }

    @Override
    public byte[] currentPage() {
        int index;
        synchronized (this) {
            ensureOpen();
            index = currentPageIndex;
        }
        return pipeline.load(index);
    }

    @Override
    public byte[] page(int index) {
        synchronized (this) {
            ensureOpen();
        }
        return pipeline.load(index);
    }

    @Override
    public synchronized void goToPage(int index) {
        ensureOpen();
        if (index < 0 || index >= pageCount) {
            throw new IllegalArgumentException("index must address an existing reader page");
        }
        if (currentPageIndex != index) {
            currentPageIndex = index;
            persistProgress();
        }
    }

    @Override
    public synchronized boolean nextPage() {
        ensureOpen();
        if (currentPageIndex + 1 >= pageCount) {
            return false;
        }
        currentPageIndex++;
        persistProgress();
        return true;
    }

    @Override
    public synchronized boolean previousPage() {
        ensureOpen();
        if (currentPageIndex == 0) {
            return false;
        }
        currentPageIndex--;
        persistProgress();
        return true;
    }

    @Override
    public synchronized void setDirection(ReadingDirection nextDirection) {
        ensureOpen();
        direction = Objects.requireNonNull(nextDirection, "direction must not be null");
    }

    private void persistProgress() {
        if (!persistenceAllowed.getAsBoolean()) {
            return;
        }
        LibraryItem current = library.find(libraryItemId)
                .orElseThrow(() -> new ReaderException("Library item disappeared while reading"));
        Instant now = clock.instant();
        library.save(current.withProgress(new LibraryProgress(
                contentUnit.id().value(),
                currentPageIndex,
                pageCount - 1L,
                now)));
    }

    private void ensureOpen() {
        if (closed) {
            throw new ReaderException("Reader session is closed");
        }
    }

    @Override
    public synchronized void close() {
        if (!closed) {
            closed = true;
            pipeline.close();
            onClose.run();
        }
    }
}
