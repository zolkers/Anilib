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
        if (result == 0) {
            result = Integer.compare(patch, other.patch);
        }
        if (result != 0) {
            return result;
        }
        String qualifier = qualifier(display);
        String otherQualifier = qualifier(other.display);
        if (qualifier.isEmpty() || otherQualifier.isEmpty()) {
            return qualifier.isEmpty() ? (otherQualifier.isEmpty() ? 0 : 1) : -1;
        }
        return compareQualifier(qualifier, otherQualifier);
    }

    private static String qualifier(String value) {
        int dash = value.indexOf('-');
        if (dash < 0) {
            return "";
        }
        int build = value.indexOf('+', dash);
        return value.substring(dash + 1, build < 0 ? value.length() : build);
    }

    private static int compareQualifier(String left, String right) {
        String[] leftParts = left.split("\\.");
        String[] rightParts = right.split("\\.");
        for (int index = 0; index < Math.max(leftParts.length, rightParts.length); index++) {
            if (index >= leftParts.length || index >= rightParts.length) {
                return Integer.compare(leftParts.length, rightParts.length);
            }
            String leftPart = leftParts[index];
            String rightPart = rightParts[index];
            int order;
            if (leftPart.matches("\\d+") && rightPart.matches("\\d+")) {
                order = compareNumericIdentifier(leftPart, rightPart);
            } else if (leftPart.matches("\\d+")) {
                order = -1;
            } else if (rightPart.matches("\\d+")) {
                order = 1;
            } else {
                order = leftPart.compareToIgnoreCase(rightPart);
            }
            if (order != 0) {
                return order;
            }
        }
        return 0;
    }

    private static int compareNumericIdentifier(String left, String right) {
        String normalizedLeft = left.replaceFirst("^0+(?!$)", "");
        String normalizedRight = right.replaceFirst("^0+(?!$)", "");
        int lengthOrder = Integer.compare(normalizedLeft.length(), normalizedRight.length());
        return lengthOrder != 0 ? lengthOrder : normalizedLeft.compareTo(normalizedRight);
    }
}
