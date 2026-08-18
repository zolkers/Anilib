package fr.vriege.anilib.feature.player.ui;

import fr.vriege.anilib.feature.player.PlayerSession;
import fr.vriege.anilib.feature.player.PlayerSessionSnapshot;
import fr.vriege.anilib.feature.player.PlayerPlayback;

import java.util.Objects;
import java.util.Optional;

public final class PlayerController implements AutoCloseable {
    private final PlayerSession session;

    PlayerController(PlayerSession session) {
        this.session = Objects.requireNonNull(session, "session must not be null");
    }

    public PlayerSessionSnapshot snapshot() {
        return session.snapshot();
    }

    public PlayerPlayback playback() {
        return session.playback();
    }

    public void selectStream(String streamId) {
        session.selectStream(streamId);
    }

    public void selectSubtitle(Optional<String> subtitleId) {
        session.selectSubtitle(subtitleId);
    }

    public void play() {
        session.play();
    }

    public void pause() {
        session.pause();
    }

    public void seekTo(long positionMillis) {
        session.seekTo(positionMillis);
    }

    public void setVolume(float volume) {
        session.setVolume(volume);
    }

    public void setPlaybackSpeed(float speed) {
        session.setPlaybackSpeed(speed);
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
