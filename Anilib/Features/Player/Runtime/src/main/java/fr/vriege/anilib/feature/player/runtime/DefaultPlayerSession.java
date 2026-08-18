package fr.vriege.anilib.feature.player.runtime;

import fr.vriege.anilib.feature.player.PlaybackState;
import fr.vriege.anilib.feature.player.PlayerException;
import fr.vriege.anilib.feature.player.PlayerSession;
import fr.vriege.anilib.feature.player.PlayerSessionSnapshot;
import fr.vriege.anilib.feature.source.SourceSubtitleTrack;
import fr.vriege.anilib.feature.source.SourceVideoStream;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Default in-memory selection session delegating durable progress to its service. */
final class DefaultPlayerSession implements PlayerSession {
    private final DefaultPlayerService service;
    private final PlayerSessionSnapshot initial;
    private String selectedStreamId;
    private Optional<String> selectedSubtitleId;
    private PlaybackState playback;
    private boolean closed;

    DefaultPlayerSession(DefaultPlayerService service, PlayerSessionSnapshot initial) {
        this.service = Objects.requireNonNull(service, "service must not be null");
        this.initial = Objects.requireNonNull(initial, "initial must not be null");
        selectedStreamId = initial.selectedStreamId();
        selectedSubtitleId = initial.selectedSubtitleId();
        playback = initial.playback();
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
        selectedStreamId = selected.id();
        if (selectedSubtitleId.isPresent() && selected.subtitles().stream()
                .noneMatch(track -> track.id().equals(selectedSubtitleId.orElseThrow()))) {
            selectedSubtitleId = Optional.empty();
        }
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
        selectedSubtitleId = requested;
    }

    @Override
    public synchronized void updatePlayback(long positionMillis, long durationMillis) {
        ensureOpen();
        playback = service.updatePlayback(playback, positionMillis, durationMillis, false);
    }

    @Override
    public synchronized void markCompleted() {
        ensureOpen();
        long position = playback.durationMillis() < 0
                ? playback.positionMillis()
                : playback.durationMillis();
        playback = service.updatePlayback(playback, position, playback.durationMillis(), true);
    }

    private SourceVideoStream stream(String id) {
        String value = Objects.requireNonNull(id, "streamId must not be null");
        return initial.streams().stream()
                .filter(stream -> stream.id().equals(value))
                .findFirst()
                .orElseThrow(() -> new PlayerException("Unknown video stream: " + value));
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
            service.removeSession(this);
        }
    }
}
