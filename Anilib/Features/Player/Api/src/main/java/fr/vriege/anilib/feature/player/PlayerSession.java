package fr.vriege.anilib.feature.player;

import java.util.Optional;

/** Mutable playback selection and resume session owned by the Player service. */
public interface PlayerSession extends AutoCloseable {
    PlayerSessionSnapshot snapshot();

    void selectStream(String streamId);

    void selectSubtitle(Optional<String> subtitleId);

    void updatePlayback(long positionMillis, long durationMillis);

    void markCompleted();

    @Override
    void close();
}
