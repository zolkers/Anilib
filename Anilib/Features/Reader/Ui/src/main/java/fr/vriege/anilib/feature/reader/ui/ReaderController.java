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
        this.session = reader.open(libraryItemId);
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
    }

    public boolean nextPage() {
        return session.nextPage();
    }

    public boolean previousPage() {
        return session.previousPage();
    }

    public void setDirection(ReadingDirection direction) {
        session.setDirection(direction);
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
        return moveContentUnit(1);
    }

    public boolean previousContentUnit() {
        return moveContentUnit(-1);
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
        if (titleOverride && libraryItemId != null) {
            display.saveOverride(session.snapshot().libraryItemId(), preferences);
        } else {
            display.save(preferences);
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
                return true;
            }
        }
        return false;
    }
}
