package fr.vriege.anilib.feature.player;

import java.util.Optional;

public interface PlayerPlayback extends AutoCloseable {
    PlayerMedia media();

    PlayerPlaybackSnapshot snapshot();

    void play();

    void pause();

    void seekTo(long positionMillis);

    void setVolume(float volume);

    void setPlaybackSpeed(float speed);

    void selectSubtitle(Optional<String> subtitleId);

    @Override
    void close();
}
