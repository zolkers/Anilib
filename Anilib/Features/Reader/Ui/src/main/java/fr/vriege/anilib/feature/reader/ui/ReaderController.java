package fr.vriege.anilib.feature.reader.ui;

import fr.vriege.anilib.feature.reader.ReaderSession;
import fr.vriege.anilib.feature.reader.ReaderSessionSnapshot;
import fr.vriege.anilib.feature.reader.ReadingDirection;

import java.util.Objects;

public final class ReaderController implements AutoCloseable {
    private final ReaderSession session;

    ReaderController(ReaderSession session) {
        this.session = Objects.requireNonNull(session, "session must not be null");
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

    @Override
    public void close() {
        session.close();
    }
}
