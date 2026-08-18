package fr.vriege.anilib.feature.tracker;

import fr.vriege.anilib.foundation.validation.Preconditions;

import java.util.Locale;

public record TrackerIcon(String monogram, int colorRgb) {
    public TrackerIcon {
        monogram = Preconditions.requireNonBlank(monogram, "monogram").strip().toUpperCase(Locale.ROOT);
        if (monogram.codePointCount(0, monogram.length()) > 3) {
            throw new IllegalArgumentException("monogram must contain at most three characters");
        }
        if (colorRgb < 0 || colorRgb > 0xFFFFFF) {
            throw new IllegalArgumentException("colorRgb must be a 24-bit RGB color");
        }
    }

    public static TrackerIcon generic(String providerName) {
        String name = Preconditions.requireNonBlank(providerName, "providerName").strip();
        return new TrackerIcon(name.substring(0, name.offsetByCodePoints(0, 1)), 0x607D8B);
    }
}
