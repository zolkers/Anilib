package fr.vriege.anilib.feature.player;

import fr.vriege.anilib.feature.library.LibraryItemId;
import fr.vriege.anilib.feature.source.SourceEpisode;
import fr.vriege.anilib.feature.source.SourceVideoStream;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Complete platform-neutral selection and resume state for one episode. */
public record PlayerSessionSnapshot(
        LibraryItemId libraryItemId,
        String title,
        SourceEpisode episode,
        List<SourceVideoStream> streams,
        String selectedStreamId,
        Optional<String> selectedSubtitleId,
        PlaybackState playback) {
    public PlayerSessionSnapshot {
        Objects.requireNonNull(libraryItemId, "libraryItemId must not be null");
        if (Objects.requireNonNull(title, "title must not be null").isBlank()) {
            throw new IllegalArgumentException("title must not be blank");
        }
        Objects.requireNonNull(episode, "episode must not be null");
        streams = List.copyOf(streams);
        if (streams.isEmpty()) {
            throw new IllegalArgumentException("streams must not be empty");
        }
        Set<String> streamIds = new HashSet<>();
        streams.forEach(stream -> {
            if (!streamIds.add(stream.id())) {
                throw new IllegalArgumentException("stream ids must be unique");
            }
        });
        if (!streamIds.contains(selectedStreamId)) {
            throw new IllegalArgumentException("selectedStreamId must reference a stream");
        }
        selectedSubtitleId = Objects.requireNonNull(
                selectedSubtitleId,
                "selectedSubtitleId must not be null");
        if (selectedSubtitleId.isPresent()) {
            String subtitleId = selectedSubtitleId.orElseThrow();
            SourceVideoStream selected = streams.stream()
                    .filter(stream -> stream.id().equals(selectedStreamId))
                    .findFirst()
                    .orElseThrow();
            if (selected.subtitles().stream().noneMatch(track -> track.id().equals(subtitleId))) {
                throw new IllegalArgumentException("selectedSubtitleId must reference the selected stream");
            }
        }
        Objects.requireNonNull(playback, "playback must not be null");
    }

    public SourceVideoStream selectedStream() {
        return streams.stream()
                .filter(stream -> stream.id().equals(selectedStreamId))
                .findFirst()
                .orElseThrow();
    }
}
