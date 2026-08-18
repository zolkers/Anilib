package fr.vriege.anilib.feature.player.ui;

import fr.vriege.anilib.feature.player.PlayerSession;
import fr.vriege.anilib.feature.player.PlayerSessionSnapshot;

import java.util.Objects;
import java.util.Optional;

/** Thin shared controller for stream, subtitle, and resume-state selection. */
public final class PlayerController implements AutoCloseable {
    private final PlayerSession session;

    PlayerController(PlayerSession session) {
        this.session = Objects.requireNonNull(session, "session must not be null");
    }

    public PlayerSessionSnapshot snapshot() {
        return session.snapshot();
    }

    public void selectStream(String streamId) {
        session.selectStream(streamId);
    }

    public void selectSubtitle(Optional<String> subtitleId) {
        session.selectSubtitle(subtitleId);
    }

    public void updatePlayback(long positionMillis, long durationMillis) {
        session.updatePlayback(positionMillis, durationMillis);
    }

    public void markCompleted() {
        session.markCompleted();
    }

    @Override
    public void close() {
        session.close();
    }
}
