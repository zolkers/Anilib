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

    /**
     * Chapters kept open at once. Enough to scroll into either neighbour without a reload, while
     * bounding how much a long reading session accumulates.
     */
    private static final int WINDOW_LIMIT = 3;

    private final ReaderService reader;
    private final LibraryItemId libraryItemId;
    private final SourceCatalogueItemId sourceItemId;
    private final String transientTitle;
    private final ReaderInteractionPreferenceStore interactions;
    private final ReaderDisplayPreferenceStore display;
    private final ReaderReadStateStore readState;
    private ReaderSession session;
    private final List<ReaderSession> windowSessions = new ArrayList<>();
    private int currentSlot;

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
        resetWindow();
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
        return interactions.snapshot().withUsableHorizontalTaps();
    }

    public void setInteractions(ReaderInteractionPreferences preferences) {
        interactions.save(Objects.requireNonNull(
                preferences,
                "preferences must not be null").withUsableHorizontalTaps());
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
        resetWindow();
        session.close();
    }


    /**
     * The chapters the continuous viewer scrolls through as one uninterrupted sequence, ordered
     * top to bottom. The window only ever grows: dropping a chapter would mutate the item list
     * around the reader's scroll anchor and make the view jump mid-gesture.
     */
    public synchronized List<ReaderWindowChapter> window() {
        ensureWindowInitialised();
        List<ReaderWindowChapter> chapters = new ArrayList<>(windowSessions.size());
        int offset = 0;
        for (int slot = 0; slot < windowSessions.size(); slot++) {
            ReaderSessionSnapshot snapshot = windowSessions.get(slot).snapshot();
            chapters.add(new ReaderWindowChapter(
                    snapshot.contentUnit(),
                    snapshot.pageCount(),
                    offset,
                    slot == currentSlot));
            offset += snapshot.pageCount();
        }
        return List.copyOf(chapters);
    }

    /** Global index of the current chapter's current page within {@link #window()}. */
    public synchronized int windowPageIndex() {
        ensureWindowInitialised();
        int offset = 0;
        for (int slot = 0; slot < currentSlot; slot++) {
            offset += windowSessions.get(slot).snapshot().pageCount();
        }
        return offset + session.snapshot().currentPageIndex();
    }

    /** Reads a page addressed by its index in the flattened window sequence. */
    public byte[] windowPage(int globalPage) {
        ReaderSession target;
        int local;
        // Resolve the owning session under the lock, then read the page outside it: page loads hit
        // the network or disk and must never serialise behind the controller monitor.
        synchronized (this) {
            ensureWindowInitialised();
            int offset = 0;
            ReaderSession found = null;
            int foundLocal = 0;
            for (ReaderSession candidate : windowSessions) {
                int count = candidate.snapshot().pageCount();
                if (globalPage < offset + count) {
                    found = candidate;
                    foundLocal = globalPage - offset;
                    break;
                }
                offset += count;
            }
            if (found == null) {
                throw new IllegalArgumentException("globalPage must address a page inside the window");
            }
            target = found;
            local = foundLocal;
        }
        return target.page(local);
    }

    /**
     * Reports the page the viewer scrolled to. Staying inside the current chapter only records
     * progress; scrolling into an already-loaded chapter promotes it to current without reopening
     * it and without touching the window, so the scroll position is never disturbed.
     *
     * @return true when the current chapter changed
     */
    public synchronized boolean selectWindowPage(int globalPage) {
        ensureWindowInitialised();
        int offset = 0;
        for (int slot = 0; slot < windowSessions.size(); slot++) {
            ReaderSession candidate = windowSessions.get(slot);
            int count = candidate.snapshot().pageCount();
            if (globalPage < offset + count) {
                int local = globalPage - offset;
                if (slot == currentSlot) {
                    goToPage(local);
                    return false;
                }
                currentSlot = slot;
                session = candidate;
                session.goToPage(local);
                markReadWhenComplete();
                return true;
            }
            offset += count;
        }
        return false;
    }

    /**
     * Extends the window with the chapters adjacent to the current one. Costly - it queries the
     * source and builds a page pipeline per chapter - so callers must invoke it off the UI thread,
     * and only once the reader is near an edge of the window.
     */
    public synchronized void prefetchNeighbours() {
        ensureWindowInitialised();
        List<SourceContentUnit> units;
        try {
            units = contentUnits();
        } catch (RuntimeException ignored) {
            return;
        }
        int topIndex = sourceIndex(units, windowSessions.get(0));
        int bottomIndex = sourceIndex(units, windowSessions.get(windowSessions.size() - 1));
        if (topIndex >= 0 && topIndex + OLDER < units.size()) {
            ReaderSession older = openNeighbour(units.get(topIndex + OLDER).id());
            if (older != null) {
                windowSessions.add(0, older);
                currentSlot++;
            }
        }
        if (bottomIndex >= 0 && bottomIndex + NEWER >= 0) {
            ReaderSession newer = openNeighbour(units.get(bottomIndex + NEWER).id());
            if (newer != null) {
                windowSessions.add(newer);
            }
        }
        trimWindow();
    }

    /**
     * Releases chapters that fell outside the window. Only the far end is trimmed, never a chapter
     * adjacent to the current one, so the item list never changes near the reader's scroll anchor.
     */
    private void trimWindow() {
        while (windowSessions.size() > WINDOW_LIMIT) {
            int distanceToTop = currentSlot;
            int distanceToBottom = windowSessions.size() - 1 - currentSlot;
            if (distanceToTop <= 1 && distanceToBottom <= 1) {
                return;
            }
            int victim = distanceToTop >= distanceToBottom ? 0 : windowSessions.size() - 1;
            ReaderSession dropped = windowSessions.remove(victim);
            if (victim == 0) {
                currentSlot--;
            }
            dropped.close();
        }
    }

    private int sourceIndex(List<SourceContentUnit> units, ReaderSession candidate) {
        SourceContentUnitId id = candidate.snapshot().contentUnit().id();
        for (int index = 0; index < units.size(); index++) {
            if (units.get(index).id().equals(id)) {
                return index;
            }
        }
        return -1;
    }

    private void ensureWindowInitialised() {
        if (windowSessions.isEmpty()) {
            windowSessions.add(session);
            currentSlot = 0;
        }
    }

    private ReaderSession openNeighbour(SourceContentUnitId contentUnitId) {
        for (ReaderSession existing : windowSessions) {
            if (existing.snapshot().contentUnit().id().equals(contentUnitId)) {
                return null;
            }
        }
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

    private void resetWindow() {
        for (ReaderSession windowed : windowSessions) {
            if (windowed != session) {
                windowed.close();
            }
        }
        windowSessions.clear();
        currentSlot = 0;
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
