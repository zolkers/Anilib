package fr.vriege.anilib.feature.player;

import java.util.Objects;
import java.util.Optional;

final class UnavailablePlayerBackend implements PlayerBackend {
    @Override
    public String id() {
        return "unavailable";
    }

    @Override
    public boolean available() {
        return false;
    }

    @Override
    public PlayerPlayback open(PlayerMedia media) {
        return new UnavailablePlayerPlayback(media);
    }

    private static final class UnavailablePlayerPlayback implements PlayerPlayback {
        private final PlayerMedia media;
        private PlayerPlaybackSnapshot snapshot;
        private boolean closed;

        private UnavailablePlayerPlayback(PlayerMedia media) {
            this.media = Objects.requireNonNull(media, "media must not be null");
            snapshot = new PlayerPlaybackSnapshot(
                    PlayerPlaybackStatus.UNAVAILABLE,
                    media.startPositionMillis(),
                    PlaybackState.UNKNOWN_DURATION,
                    1.0f,
                    1.0f,
                    Optional.empty());
        }

        @Override
        public synchronized PlayerMedia media() {
            ensureOpen();
            return media;
        }

        @Override
        public synchronized PlayerPlaybackSnapshot snapshot() {
            ensureOpen();
            return snapshot;
        }

        @Override
        public synchronized void play() {
            ensureOpen();
        }

        @Override
        public synchronized void pause() {
            ensureOpen();
        }

        @Override
        public synchronized void seekTo(long positionMillis) {
            ensureOpen();
            if (positionMillis < 0) {
                throw new IllegalArgumentException("positionMillis must not be negative");
            }
            snapshot = new PlayerPlaybackSnapshot(
                    PlayerPlaybackStatus.UNAVAILABLE,
                    positionMillis,
                    snapshot.durationMillis(),
                    snapshot.volume(),
                    snapshot.playbackSpeed(),
                    Optional.empty());
        }

        @Override
        public synchronized void setVolume(float volume) {
            ensureOpen();
            snapshot = new PlayerPlaybackSnapshot(
                    snapshot.status(),
                    snapshot.positionMillis(),
                    snapshot.durationMillis(),
                    volume,
                    snapshot.playbackSpeed(),
                    snapshot.errorMessage());
        }

        @Override
        public synchronized void setPlaybackSpeed(float speed) {
            ensureOpen();
            snapshot = new PlayerPlaybackSnapshot(
                    snapshot.status(),
                    snapshot.positionMillis(),
                    snapshot.durationMillis(),
                    snapshot.volume(),
                    speed,
                    snapshot.errorMessage());
        }

        @Override
        public synchronized void selectSubtitle(Optional<String> subtitleId) {
            ensureOpen();
            Objects.requireNonNull(subtitleId, "subtitleId must not be null");
        }

        private void ensureOpen() {
            if (closed) {
                throw new PlayerException("Player playback is closed");
            }
        }

        @Override
        public synchronized void close() {
            closed = true;
        }
    }
}
