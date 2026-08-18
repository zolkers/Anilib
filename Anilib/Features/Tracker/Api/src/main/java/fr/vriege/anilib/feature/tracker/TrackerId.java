package fr.vriege.anilib.feature.tracker;

import fr.vriege.anilib.foundation.validation.Preconditions;

public record TrackerId(String value) implements Comparable<TrackerId> {
    public TrackerId {
        Preconditions.requireNonBlank(value, "value");
    }

    public static TrackerId of(String value) {
        return new TrackerId(value);
    }

    @Override
    public int compareTo(TrackerId other) {
        return value.compareTo(other.value);
    }

    @Override
    public String toString() {
        return value;
    }
}
