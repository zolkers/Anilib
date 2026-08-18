package fr.vriege.anilib.feature.reader.ui;

import fr.vriege.anilib.feature.reader.ReaderDisplayPreferenceStore;
import fr.vriege.anilib.feature.reader.ReaderDisplayPreferences;
import fr.vriege.anilib.feature.reader.ReaderSession;
import fr.vriege.anilib.feature.reader.ReaderSessionSnapshot;
import fr.vriege.anilib.feature.reader.ReaderInteractionPreferenceStore;
import fr.vriege.anilib.feature.reader.ReaderInteractionPreferences;
import fr.vriege.anilib.feature.reader.ReadingDirection;

import java.util.Objects;

public final class ReaderController implements AutoCloseable {
    private final ReaderSession session;
    private final ReaderInteractionPreferenceStore interactions;
    private final ReaderDisplayPreferenceStore display;

    ReaderController(
            ReaderSession session,
            ReaderInteractionPreferenceStore interactions,
            ReaderDisplayPreferenceStore display) {
        this.session = Objects.requireNonNull(session, "session must not be null");
        this.interactions = Objects.requireNonNull(interactions, "interactions must not be null");
        this.display = Objects.requireNonNull(display, "display must not be null");
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

    public ReaderInteractionPreferences interactions() {
        return interactions.snapshot();
    }

    public void setInteractions(ReaderInteractionPreferences preferences) {
        interactions.save(preferences);
    }

    public ReaderDisplayPreferences display() {
        return display.snapshot();
    }

    public void setDisplay(ReaderDisplayPreferences preferences) {
        display.save(preferences);
    }

    @Override
    public void close() {
        session.close();
    }
}
