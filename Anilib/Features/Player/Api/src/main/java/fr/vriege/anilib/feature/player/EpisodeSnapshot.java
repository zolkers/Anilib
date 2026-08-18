package fr.vriege.anilib.feature.player;

import fr.vriege.anilib.feature.source.SourceEpisode;

import java.util.Objects;
import java.util.Optional;

public record EpisodeSnapshot(SourceEpisode episode, Optional<PlaybackState> playback) {
    public EpisodeSnapshot {
        Objects.requireNonNull(episode, "episode must not be null");
        Objects.requireNonNull(playback, "playback must not be null");
        playback.ifPresent(state -> {
            if (!state.episodeId().equals(episode.id())) {
                throw new IllegalArgumentException("playback must belong to the episode");
            }
        });
    }
}
