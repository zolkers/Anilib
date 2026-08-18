package fr.vriege.anilib.feature.tracker;

import fr.vriege.anilib.feature.library.LibraryItemId;
import fr.vriege.anilib.foundation.validation.Preconditions;

import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;

/** Durable local mirror of one title bound to one remote tracking service. */
public record TrackerEntry(
        LibraryItemId libraryItemId,
        TrackerId trackerId,
        String remoteId,
        String title,
        double progress,
        long totalUnits,
        TrackerStatus status,
        OptionalDouble score,
        Optional<LocalDate> startDate,
        Optional<LocalDate> finishDate,
        boolean privateEntry,
        Optional<URI> remoteUri,
        Instant updatedAt) {
    public TrackerEntry {
        Objects.requireNonNull(libraryItemId, "libraryItemId must not be null");
        Objects.requireNonNull(trackerId, "trackerId must not be null");
        Preconditions.requireNonBlank(remoteId, "remoteId");
        Preconditions.requireNonBlank(title, "title");
        if (!Double.isFinite(progress) || progress < 0.0) {
            throw new IllegalArgumentException("progress must be finite and non-negative");
        }
        if (totalUnits < TrackerSearchResult.UNKNOWN_TOTAL) {
            throw new IllegalArgumentException("totalUnits must be non-negative or unknown");
        }
        if (totalUnits >= 0 && progress > totalUnits) {
            throw new IllegalArgumentException("progress must not exceed totalUnits");
        }
        Objects.requireNonNull(status, "status must not be null");
        score = Objects.requireNonNull(score, "score must not be null");
        if (score.isPresent() && (!Double.isFinite(score.getAsDouble())
                || score.getAsDouble() < 0.0 || score.getAsDouble() > 10.0)) {
            throw new IllegalArgumentException("score must be between 0 and 10");
        }
        startDate = Objects.requireNonNull(startDate, "startDate must not be null");
        finishDate = Objects.requireNonNull(finishDate, "finishDate must not be null");
        remoteUri = Objects.requireNonNull(remoteUri, "remoteUri must not be null");
        Objects.requireNonNull(updatedAt, "updatedAt must not be null");
    }

    public TrackerEntry withProgress(double value) {
        return copy(value, status, score, startDate, finishDate, privateEntry);
    }

    public TrackerEntry withStatus(TrackerStatus value) {
        double nextProgress = value == TrackerStatus.COMPLETED && totalUnits >= 0 ? totalUnits : progress;
        return copy(nextProgress, value, score, startDate, finishDate, privateEntry);
    }

    public TrackerEntry withScore(OptionalDouble value) {
        return copy(progress, status, value, startDate, finishDate, privateEntry);
    }

    public TrackerEntry withDates(Optional<LocalDate> started, Optional<LocalDate> finished) {
        return copy(progress, status, score, started, finished, privateEntry);
    }

    public TrackerEntry withPrivateEntry(boolean value) {
        return copy(progress, status, score, startDate, finishDate, value);
    }

    private TrackerEntry copy(
            double nextProgress,
            TrackerStatus nextStatus,
            OptionalDouble nextScore,
            Optional<LocalDate> nextStart,
            Optional<LocalDate> nextFinish,
            boolean nextPrivate) {
        return new TrackerEntry(
                libraryItemId,
                trackerId,
                remoteId,
                title,
                nextProgress,
                totalUnits,
                nextStatus,
                nextScore,
                nextStart,
                nextFinish,
                nextPrivate,
                remoteUri,
                Instant.now());
    }
}
