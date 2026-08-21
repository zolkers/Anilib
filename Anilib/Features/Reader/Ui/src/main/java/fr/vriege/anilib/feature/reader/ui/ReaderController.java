package fr.vriege.anilib.feature.reader.ui;

import fr.vriege.anilib.feature.library.LibraryItemId;
import fr.vriege.anilib.feature.reader.ReaderDisplayPreferenceStore;
import fr.vriege.anilib.feature.reader.ReaderDisplayPreferences;
import fr.vriege.anilib.feature.reader.ReaderReadStateStore;
import fr.vriege.anilib.feature.reader.ReaderService;
import fr.vriege.anilib.feature.reader.ReaderSession;
import fr.vriege.anilib.feature.reader.ReaderSessionSnapshot;
import fr.vriege.anilib.feature.reader.ReaderInteractionPreferenceStore;
import fr.vriege.anilib.feature.reader.ReaderInteractionPreferences;
import fr.vriege.anilib.feature.reader.ReadingDirection;
import fr.vriege.anilib.feature.source.SourceContentUnit;
import fr.vriege.anilib.feature.source.SourceContentUnitId;
import fr.vriege.anilib.feature.source.SourceCatalogueItemId;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class ReaderController implements AutoCloseable {
    /**
     * Sources list content units newest first, so walking towards index 0 reaches newer chapters
     * and walking towards the end reaches older ones.
     */
    private static final int NEWER = -1;
    private static final int OLDER = 1;

    private final ReaderService reader;
    private final LibraryItemId libraryItemId;
    private final SourceCatalogueItemId sourceItemId;
    private final String transientTitle;
    private final ReaderInteractionPreferenceStore interactions;
    private final ReaderDisplayPreferenceStore display;
    private final ReaderReadStateStore readState;
    private ReaderSession session;
    private ReaderSession olderSession;
    private ReaderSession newerSession;

    ReaderController(
            ReaderService reader,
            LibraryItemId libraryItemId,
            SourceContentUnitId contentUnitId,
            ReaderInteractionPreferenceStore interactions,
            ReaderDisplayPreferenceStore display,
            ReaderReadStateStore readState) {
        this.reader = Objects.requireNonNull(reader, "reader must not be null");
        this.libraryItemId = Objects.requireNonNull(libraryItemId, "libraryItemId must not be null");
        this.sourceItemId = null;
        this.transientTitle = null;
        this.interactions = Objects.requireNonNull(interactions, "interactions must not be null");
        this.display = Objects.requireNonNull(display, "display must not be null");
        this.readState = Objects.requireNonNull(readState, "readState must not be null");
        this.session = contentUnitId == null
                ? reader.open(libraryItemId)
                : reader.open(libraryItemId, contentUnitId);
        restoreReadingDirection();
    }

    ReaderController(
            ReaderService reader,
            String title,
            SourceContentUnitId contentUnitId,
            ReaderInteractionPreferenceStore interactions,
            ReaderDisplayPreferenceStore display,
            ReaderReadStateStore readState) {
        this.reader = Objects.requireNonNull(reader, "reader must not be null");
        this.libraryItemId = null;
        this.sourceItemId = Objects.requireNonNull(contentUnitId, "contentUnitId must not be null").itemId();
        this.transientTitle = Objects.requireNonNull(title, "title must not be null");
        this.interactions = Objects.requireNonNull(interactions, "interactions must not be null");
        this.display = Objects.requireNonNull(display, "display must not be null");
        this.readState = Objects.requireNonNull(readState, "readState must not be null");
        this.session = reader.open(title, contentUnitId);
        restoreReadingDirection();
    }

    public ReaderSessionSnapshot snapshot() {
        return session.snapshot();
    }

    public byte[] currentPage() {
        return session.currentPage();
    }

    public byte[] page(int index) {
        return session.page(index);
    }

    public void goToPage(int index) {
        session.goToPage(index);
        markReadWhenComplete();
    }

    public boolean nextPage() {
        boolean moved = session.nextPage();
        if (moved) {
            markReadWhenComplete();
        }
        return moved;
    }

    public boolean previousPage() {
        return session.previousPage();
    }

    public void setDirection(ReadingDirection direction) {
        ReadingDirection value = Objects.requireNonNull(direction, "direction must not be null");
        LibraryItemId itemId = session.snapshot().libraryItemId();
        ReaderDisplayPreferences preferences = display.snapshot(itemId).withReadingDirection(value);
        if (display.hasOverride(itemId)) {
            display.saveOverride(itemId, preferences);
        } else {
            display.save(preferences);
        }
        session.setDirection(value);
    }

    public List<SourceContentUnit> contentUnits() {
        return libraryItemId == null
                ? reader.contentUnits(sourceItemId)
                : reader.contentUnits(libraryItemId);
    }

    public synchronized void openContentUnit(SourceContentUnitId contentUnitId) {
        closeNeighbours();
        ReadingDirection direction = session.snapshot().direction();
        ReaderSession next = libraryItemId == null
                ? reader.open(transientTitle, contentUnitId)
                : reader.open(libraryItemId, contentUnitId);
        next.setDirection(direction);
        ReaderSession previous = session;
        session = next;
        previous.close();
    }

    public boolean nextContentUnit() {
        return moveContentUnit(NEWER);
    }

    public boolean previousContentUnit() {
        return moveContentUnit(OLDER);
    }

    public Set<String> readContentIds() {
        return libraryItemId == null ? Set.of() : readState.readContentIds(libraryItemId);
    }

    public void setContentRead(SourceContentUnitId contentUnitId, boolean read) {
        if (libraryItemId != null) {
            readState.setRead(libraryItemId, contentUnitId.value(), read);
        }
    }

    public ReaderInteractionPreferences interactions() {
        return interactions.snapshot();
    }

    public void setInteractions(ReaderInteractionPreferences preferences) {
        interactions.save(preferences);
    }

    public ReaderDisplayPreferences display() {
        return display.snapshot(session.snapshot().libraryItemId());
    }

    public boolean hasDisplayOverride() {
        return display.hasOverride(session.snapshot().libraryItemId());
    }

    public void setDisplay(ReaderDisplayPreferences preferences, boolean titleOverride) {
        ReaderDisplayPreferences value = Objects.requireNonNull(
                preferences,
                "preferences must not be null").withReadingDirection(session.snapshot().direction());
        if (titleOverride && libraryItemId != null) {
            display.saveOverride(session.snapshot().libraryItemId(), value);
        } else {
            display.save(value);
        }
    }

    public void clearDisplayOverride() {
        display.clearOverride(session.snapshot().libraryItemId());
    }

    @Override
    public synchronized void close() {
        closeNeighbours();
        session.close();
    }


    /**
     * The chapters the continuous viewer scrolls through as one uninterrupted sequence: the
     * current chapter plus its already-resolved neighbours. Mirrors how Aniyomi keeps previous,
     * current and next pages in a single list so crossing a chapter never rebuilds the view.
     */
    public synchronized List<ReaderWindowChapter> window() {
        refreshNeighbours();
        List<ReaderWindowChapter> chapters = new ArrayList<>(3);
        int offset = 0;
        if (olderSession != null) {
            ReaderSessionSnapshot older = olderSession.snapshot();
            chapters.add(new ReaderWindowChapter(older.contentUnit(), older.pageCount(), offset, false));
            offset += older.pageCount();
        }
        ReaderSessionSnapshot current = session.snapshot();
        chapters.add(new ReaderWindowChapter(current.contentUnit(), current.pageCount(), offset, true));
        offset += current.pageCount();
        if (newerSession != null) {
            ReaderSessionSnapshot newer = newerSession.snapshot();
            chapters.add(new ReaderWindowChapter(newer.contentUnit(), newer.pageCount(), offset, false));
        }
        return List.copyOf(chapters);
    }

    /** Global index of the current chapter's current page within {@link #window()}. */
    public synchronized int windowPageIndex() {
        refreshNeighbours();
        int offset = olderSession == null ? 0 : olderSession.snapshot().pageCount();
        return offset + session.snapshot().currentPageIndex();
    }

    /** Reads a page addressed by its index in the flattened window sequence. */
    public byte[] windowPage(int globalPage) {
        ReaderSession target;
        int local;
        synchronized (this) {
            refreshNeighbours();
            int offset = 0;
            if (olderSession != null) {
                int count = olderSession.snapshot().pageCount();
                if (globalPage < count) {
                    target = olderSession;
                    local = globalPage;
                    return target.page(local);
                }
                offset = count;
            }
            int currentCount = session.snapshot().pageCount();
            if (globalPage < offset + currentCount) {
                target = session;
                local = globalPage - offset;
            } else if (newerSession != null) {
                target = newerSession;
                local = globalPage - offset - currentCount;
            } else {
                throw new IllegalArgumentException("globalPage must address a page inside the window");
            }
        }
        return target.page(local);
    }

    /**
     * Reports the page the viewer scrolled to. Staying inside the current chapter only records
     * progress; scrolling into a neighbour promotes that neighbour to current without reopening it,
     * so the scroll position is never disturbed.
     *
     * @return true when the current chapter changed
     */
    public synchronized boolean selectWindowPage(int globalPage) {
        refreshNeighbours();
        int olderCount = olderSession == null ? 0 : olderSession.snapshot().pageCount();
        int currentCount = session.snapshot().pageCount();
        if (globalPage < olderCount) {
            promote(olderSession, globalPage);
            return true;
        }
        if (globalPage < olderCount + currentCount) {
            goToPage(globalPage - olderCount);
            return false;
        }
        if (newerSession != null) {
            promote(newerSession, globalPage - olderCount - currentCount);
            return true;
        }
        return false;
    }

    private void promote(ReaderSession target, int pageIndex) {
        ReaderSession displaced = session;
        boolean towardsNewer = target == newerSession;
        session = target;
        if (towardsNewer) {
            if (olderSession != null) {
                olderSession.close();
            }
            olderSession = displaced;
            newerSession = null;
        } else {
            if (newerSession != null) {
                newerSession.close();
            }
            newerSession = displaced;
            olderSession = null;
        }
        session.goToPage(pageIndex);
        markReadWhenComplete();
        refreshNeighbours();
    }

    private void refreshNeighbours() {
        if (olderSession != null && newerSession != null) {
            return;
        }
        List<SourceContentUnit> units;
        try {
            units = contentUnits();
        } catch (RuntimeException ignored) {
            return;
        }
        SourceContentUnitId current = session.snapshot().contentUnit().id();
        int index = -1;
        for (int candidate = 0; candidate < units.size(); candidate++) {
            if (units.get(candidate).id().equals(current)) {
                index = candidate;
                break;
            }
        }
        if (index < 0) {
            return;
        }
        if (olderSession == null && index + OLDER >= 0 && index + OLDER < units.size()) {
            olderSession = openNeighbour(units.get(index + OLDER).id());
        }
        if (newerSession == null && index + NEWER >= 0 && index + NEWER < units.size()) {
            newerSession = openNeighbour(units.get(index + NEWER).id());
        }
    }

    private ReaderSession openNeighbour(SourceContentUnitId contentUnitId) {
        try {
            ReaderSession neighbour = libraryItemId == null
                    ? reader.open(transientTitle, contentUnitId)
                    : reader.open(libraryItemId, contentUnitId);
            neighbour.setDirection(session.snapshot().direction());
            return neighbour;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private void closeNeighbours() {
        if (olderSession != null) {
            olderSession.close();
            olderSession = null;
        }
        if (newerSession != null) {
            newerSession.close();
            newerSession = null;
        }
    }

    private boolean moveContentUnit(int delta) {
        List<SourceContentUnit> units = contentUnits();
        SourceContentUnitId current = session.snapshot().contentUnit().id();
        for (int index = 0; index < units.size(); index++) {
            if (units.get(index).id().equals(current)) {
                int target = index + delta;
                if (target < 0 || target >= units.size()) {
                    return false;
                }
                openContentUnit(units.get(target).id());
                if (delta == OLDER) {
                    // Reading backwards lands on the last page so the chapter continues seamlessly.
                    session.goToPage(session.snapshot().pageCount() - 1);
                }
                return true;
            }
        }
        return false;
    }

    private void markReadWhenComplete() {
        if (libraryItemId == null) {
            return;
        }
        ReaderSessionSnapshot current = session.snapshot();
        if (current.currentPageIndex() >= current.pageCount() - 1) {
            readState.setRead(libraryItemId, current.contentUnit().id().value(), true);
        }
    }

    private void restoreReadingDirection() {
        session.setDirection(display.snapshot(session.snapshot().libraryItemId()).readingDirection());
    }
}
