package fr.vriege.anilib.feature.tracker;

import fr.vriege.anilib.feature.library.MediaKind;
import fr.vriege.anilib.foundation.validation.Preconditions;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Immutable tracker capabilities used by the shared settings and title UI. */
public record TrackerDescriptor(
        TrackerId id,
        String name,
        TrackerApiVersion requiredApiVersion,
        Set<MediaKind> supportedKinds,
        TrackerAuthentication authentication,
        List<TrackerStatus> statuses,
        List<Double> scores,
        boolean supportsDates,
        boolean supportsPrivateEntries) {
    public TrackerDescriptor {
        Objects.requireNonNull(id, "id must not be null");
        Preconditions.requireNonBlank(name, "name");
        Objects.requireNonNull(requiredApiVersion, "requiredApiVersion must not be null");
        supportedKinds = Set.copyOf(supportedKinds);
        if (supportedKinds.isEmpty()) {
            throw new IllegalArgumentException("supportedKinds must not be empty");
        }
        Objects.requireNonNull(authentication, "authentication must not be null");
        statuses = List.copyOf(statuses);
        if (statuses.isEmpty() || statuses.size() != Set.copyOf(statuses).size()) {
            throw new IllegalArgumentException("statuses must be non-empty and unique");
        }
        scores = List.copyOf(scores);
        if (scores.stream().anyMatch(score -> !Double.isFinite(score) || score < 0.0 || score > 10.0)
                || scores.size() != Set.copyOf(scores).size()) {
            throw new IllegalArgumentException("scores must be unique values between 0 and 10");
        }
    }
}
