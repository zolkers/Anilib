package fr.vriege.anilib.feature.tracker;

import fr.vriege.anilib.feature.library.MediaKind;
import fr.vriege.anilib.foundation.validation.Preconditions;

import java.net.URI;
import java.util.Objects;
import java.util.Optional;

/** Remote title candidate returned by a tracker search. */
public record TrackerSearchResult(
        TrackerId trackerId,
        String remoteId,
        String title,
        MediaKind kind,
        long totalUnits,
        Optional<URI> remoteUri) {
    public static final long UNKNOWN_TOTAL = -1L;

    public TrackerSearchResult {
        Objects.requireNonNull(trackerId, "trackerId must not be null");
        Preconditions.requireNonBlank(remoteId, "remoteId");
        Preconditions.requireNonBlank(title, "title");
        Objects.requireNonNull(kind, "kind must not be null");
        if (totalUnits < UNKNOWN_TOTAL) {
            throw new IllegalArgumentException("totalUnits must be non-negative or unknown");
        }
        remoteUri = Objects.requireNonNull(remoteUri, "remoteUri must not be null");
    }
}
