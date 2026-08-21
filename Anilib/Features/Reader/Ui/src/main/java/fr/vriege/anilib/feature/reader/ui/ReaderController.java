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

    public void openContentUnit(SourceContentUnitId contentUnitId) {
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
    public void close() {
        session.close();
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
