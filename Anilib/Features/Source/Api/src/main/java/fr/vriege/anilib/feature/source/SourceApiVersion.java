package fr.vriege.anilib.feature.source;

/** Major/minor compatibility level required by a source extension. */
public record SourceApiVersion(int major, int minor) implements Comparable<SourceApiVersion> {
    public SourceApiVersion {
        if (major < 1) {
            throw new IllegalArgumentException("major must be at least 1");
        }
        if (minor < 0) {
            throw new IllegalArgumentException("minor must not be negative");
        }
    }

    public boolean supports(SourceApiVersion required) {
        return major == required.major && minor >= required.minor;
    }

    @Override
    public int compareTo(SourceApiVersion other) {
        int majorComparison = Integer.compare(major, other.major);
        return majorComparison != 0 ? majorComparison : Integer.compare(minor, other.minor);
    }

    @Override
    public String toString() {
        return major + "." + minor;
    }
}
