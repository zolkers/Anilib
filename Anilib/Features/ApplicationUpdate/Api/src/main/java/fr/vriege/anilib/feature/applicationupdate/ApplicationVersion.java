package fr.vriege.anilib.feature.applicationupdate;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record ApplicationVersion(int major, int minor, int patch, String display)
        implements Comparable<ApplicationVersion> {
    private static final Pattern VERSION = Pattern.compile("^v?(\\d+)\\.(\\d+)\\.(\\d+)(?:[-+].*)?$");

    public ApplicationVersion {
        if (major < 0 || minor < 0 || patch < 0) {
            throw new IllegalArgumentException("version numbers must not be negative");
        }
        display = Objects.requireNonNull(display, "display must not be null").strip();
        if (display.isEmpty()) {
            throw new IllegalArgumentException("display must not be blank");
        }
    }

    public static ApplicationVersion parse(String value) {
        String text = Objects.requireNonNull(value, "value must not be null").strip();
        Matcher matcher = VERSION.matcher(text);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Application version must use MAJOR.MINOR.PATCH");
        }
        return new ApplicationVersion(
                Integer.parseInt(matcher.group(1)),
                Integer.parseInt(matcher.group(2)),
                Integer.parseInt(matcher.group(3)),
                text);
    }

    @Override
    public int compareTo(ApplicationVersion other) {
        int result = Integer.compare(major, other.major);
        if (result == 0) {
            result = Integer.compare(minor, other.minor);
        }
        return result == 0 ? Integer.compare(patch, other.patch) : result;
    }
}
