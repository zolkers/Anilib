package fr.vriege.anilib.feature.player.runtime;

import fr.vriege.anilib.feature.player.PlaybackState;
import fr.vriege.anilib.feature.player.PlayerBackend;
import fr.vriege.anilib.feature.player.PlayerDecoderPolicy;
import fr.vriege.anilib.feature.player.PlayerException;
import fr.vriege.anilib.feature.player.PlayerMedia;
import fr.vriege.anilib.feature.player.PlayerPlayback;
import fr.vriege.anilib.feature.player.PlayerSession;
import fr.vriege.anilib.feature.player.PlayerSessionSnapshot;
import fr.vriege.anilib.feature.source.SourceSubtitleTrack;
import fr.vriege.anilib.feature.source.SourceVideoStream;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

final class DefaultPlayerSession implements PlayerSession {
    private final DefaultPlayerService service;
    private final PlayerSessionSnapshot initial;
    private final PlayerBackend backend;
    private String selectedStreamId;
    private Optional<String> selectedSubtitleId;
    private PlaybackState playback;
    private PlayerPlayback mediaPlayback;
    private PlayerDecoderPolicy decoderPolicy = PlayerDecoderPolicy.AUTOMATIC;
    private Optional<String> preferredAudioLanguage = Optional.empty();
    private int completionThresholdPercent = 85;
    private boolean closed;

    DefaultPlayerSession(
            DefaultPlayerService service,
            PlayerBackend backend,
            PlayerSessionSnapshot initial) {
        this.service = Objects.requireNonNull(service, "service must not be null");
        this.backend = Objects.requireNonNull(backend, "backend must not be null");
        this.initial = Objects.requireNonNull(initial, "initial must not be null");
        selectedStreamId = initial.selectedStreamId();
        selectedSubtitleId = initial.selectedSubtitleId();
        playback = initial.playback();
        mediaPlayback = openPlayback(initial.selectedStream(), selectedSubtitleId);
    }

    @Override
    public synchronized PlayerPlayback playback() {
        ensureOpen();
        return mediaPlayback;
    }

    @Override
    public synchronized PlayerSessionSnapshot snapshot() {
        ensureOpen();
        return new PlayerSessionSnapshot(
                initial.libraryItemId(),
                initial.title(),
                initial.episode(),
                initial.streams(),
                selectedStreamId,
                selectedSubtitleId,
                playback);
    }

    @Override
    public synchronized void selectStream(String streamId) {
        ensureOpen();
        SourceVideoStream selected = stream(streamId);
        Optional<String> subtitle = selectedSubtitleId;
        String subtitleValue = subtitle.orElse(null);
        if (subtitleValue != null && selected.subtitles().stream()
                .noneMatch(track -> track.id().equals(subtitleValue))) {
            subtitle = Optional.empty();
        }
        PlayerPlayback replacement = openPlayback(selected, subtitle);
        try {
            mediaPlayback.close();
        } catch (RuntimeException failure) {
            try {
                replacement.close();
            } catch (RuntimeException cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
            throw failure;
        }
        mediaPlayback = replacement;
        selectedStreamId = selected.id();
        selectedSubtitleId = subtitle;
    }

    @Override
    public synchronized void selectSubtitle(Optional<String> subtitleId) {
        ensureOpen();
        Optional<String> requested = Objects.requireNonNull(
                subtitleId,
                "subtitleId must not be null").map(String::strip).filter(value -> !value.isEmpty());
        if (requested.isPresent()) {
            List<SourceSubtitleTrack> subtitles = stream(selectedStreamId).subtitles();
            if (subtitles.stream().noneMatch(track -> track.id().equals(requested.orElseThrow()))) {
                throw new PlayerException("Subtitle does not belong to the selected stream");
            }
        }
        mediaPlayback.selectSubtitle(requested);
        selectedSubtitleId = requested;
    }

    @Override
    public synchronized void setMediaPolicy(
            PlayerDecoderPolicy requestedDecoderPolicy,
            Optional<String> requestedAudioLanguage) {
        ensureOpen();
        PlayerDecoderPolicy decoder = Objects.requireNonNull(
                requestedDecoderPolicy,
                "decoderPolicy must not be null");
        Optional<String> audioLanguage = Objects.requireNonNull(
                requestedAudioLanguage,
                "preferredAudioLanguage must not be null")
                .map(String::strip)
                .filter(value -> !value.isEmpty());
        if (decoder == decoderPolicy && audioLanguage.equals(preferredAudioLanguage)) {
            return;
        }
        PlayerDecoderPolicy previousDecoder = decoderPolicy;
        Optional<String> previousAudioLanguage = preferredAudioLanguage;
        decoderPolicy = decoder;
        preferredAudioLanguage = audioLanguage;
        PlayerPlayback replacement;
        try {
            replacement = openPlayback(stream(selectedStreamId), selectedSubtitleId);
        } catch (RuntimeException failure) {
            decoderPolicy = previousDecoder;
            preferredAudioLanguage = previousAudioLanguage;
            throw failure;
        }
        replacePlayback(replacement);
    }

    @Override
    public synchronized void setCompletionThresholdPercent(int thresholdPercent) {
        ensureOpen();
        if (thresholdPercent < 1 || thresholdPercent > 100) {
            throw new IllegalArgumentException("thresholdPercent must be between one and 100");
        }
        completionThresholdPercent = thresholdPercent;
    }

    @Override
    public synchronized void play() {
        ensureOpen();
        mediaPlayback.play();
    }

    @Override
    public synchronized void pause() {
        ensureOpen();
        mediaPlayback.pause();
    }

    @Override
    public synchronized void seekTo(long positionMillis) {
        ensureOpen();
        mediaPlayback.seekTo(positionMillis);
    }

    @Override
    public synchronized void setVolume(float volume) {
        ensureOpen();
        mediaPlayback.setVolume(volume);
    }

    @Override
    public synchronized void setPlaybackSpeed(float speed) {
        ensureOpen();
        mediaPlayback.setPlaybackSpeed(speed);
    }

    @Override
    public synchronized void updatePlayback(long positionMillis, long durationMillis) {
        ensureOpen();
        boolean completed = durationMillis > 0L
                && (double) positionMillis / durationMillis >= completionThresholdPercent / 100.0d;
        playback = service.updatePlayback(
                playback,
                initial.episode().episodeNumber(),
                positionMillis,
                durationMillis,
                completed);
    }

    @Override
    public synchronized void markCompleted() {
        ensureOpen();
        long position = playback.durationMillis() < 0
                ? playback.positionMillis()
                : playback.durationMillis();
        playback = service.updatePlayback(
                playback,
                initial.episode().episodeNumber(),
                position,
                playback.durationMillis(),
                true);
    }

    private SourceVideoStream stream(String id) {
        String value = Objects.requireNonNull(id, "streamId must not be null");
        return initial.streams().stream()
                .filter(stream -> stream.id().equals(value))
                .findFirst()
                .orElseThrow(() -> new PlayerException("Unknown video stream: " + value));
    }

    private PlayerPlayback openPlayback(
            SourceVideoStream stream,
            Optional<String> subtitleId) {
        PlayerMedia media = new PlayerMedia(
                initial.title() + " - " + initial.episode().title(),
                stream,
                subtitleId,
                playback.positionMillis(),
                decoderPolicy,
                preferredAudioLanguage);
        return Objects.requireNonNull(
                backend.open(media),
                "player backend returned null playback");
    }

    private void replacePlayback(PlayerPlayback replacement) {
        try {
            mediaPlayback.close();
        } catch (RuntimeException failure) {
            try {
                replacement.close();
            } catch (RuntimeException cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
            throw failure;
        }
        mediaPlayback = replacement;
    }

    private void ensureOpen() {
        if (closed) {
            throw new PlayerException("Player session is closed");
        }
    }

    @Override
    public synchronized void close() {
        if (!closed) {
            closed = true;
            try {
                mediaPlayback.close();
            } finally {
                service.removeSession(this);
            }
        }
    }
}
