package fr.vriege.anilib.feature.player;

import java.util.Optional;

public interface PlayerSession extends AutoCloseable {
    PlayerSessionSnapshot snapshot();

    PlayerPlayback playback();

    boolean onlineStreamsAvailable();

    void loadOnlineStreams();

    void selectStream(String streamId);

    void selectSubtitle(Optional<String> subtitleId);

    void setMediaPolicy(PlayerDecoderPolicy decoderPolicy, Optional<String> preferredAudioLanguage);

    void setCompletionThresholdPercent(int thresholdPercent);

    void play();

    void pause();

    void seekTo(long positionMillis);

    void setVolume(float volume);

    void setPlaybackSpeed(float speed);

    void updatePlayback(long positionMillis, long durationMillis);

    void markCompleted();

    @Override
    void close();
}
