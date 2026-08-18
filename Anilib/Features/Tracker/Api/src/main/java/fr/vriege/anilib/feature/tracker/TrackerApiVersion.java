package fr.vriege.anilib.feature.tracker;

public record TrackerApiVersion(int major, int minor) implements Comparable<TrackerApiVersion> {
    public TrackerApiVersion {
        if (major < 1 || minor < 0) {
            throw new IllegalArgumentException("tracker API version must be positive");
        }
    }

    public boolean supports(TrackerApiVersion required) {
        return required != null && major == required.major && minor >= required.minor;
    }

    @Override
    public int compareTo(TrackerApiVersion other) {
        int majorOrder = Integer.compare(major, other.major);
        return majorOrder != 0 ? majorOrder : Integer.compare(minor, other.minor);
    }

    @Override
    public String toString() {
        return major + "." + minor;
    }
}
