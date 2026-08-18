package fr.vriege.anilib.feature.reader;

import java.util.Objects;

public record ReaderDisplayPreferences(
        ReaderScaleMode scaleMode,
        boolean cropBorders,
        boolean splitPages,
        ReaderRotation rotation,
        boolean dualPage,
        int webtoonSpacingDp,
        ReaderColorFilter colorFilter,
        int brightnessPercent,
        ReaderPageTransition transition,
        ReaderOrientationPolicy orientationPolicy) {
    public ReaderDisplayPreferences {
        Objects.requireNonNull(scaleMode, "scaleMode must not be null");
        Objects.requireNonNull(rotation, "rotation must not be null");
        Objects.requireNonNull(colorFilter, "colorFilter must not be null");
        Objects.requireNonNull(transition, "transition must not be null");
        Objects.requireNonNull(orientationPolicy, "orientationPolicy must not be null");
        if (webtoonSpacingDp < 0 || webtoonSpacingDp > 96) {
            throw new IllegalArgumentException("webtoonSpacingDp must be between 0 and 96");
        }
        if (splitPages && dualPage) {
            throw new IllegalArgumentException("splitPages and dualPage are mutually exclusive");
        }
        if (brightnessPercent < 25 || brightnessPercent > 200) {
            throw new IllegalArgumentException("brightnessPercent must be between 25 and 200");
        }
    }

    public static ReaderDisplayPreferences defaults() {
        return new ReaderDisplayPreferences(
                ReaderScaleMode.FIT,
                false,
                false,
                ReaderRotation.NONE,
                false,
                0,
                ReaderColorFilter.NONE,
                100,
                ReaderPageTransition.FADE,
                ReaderOrientationPolicy.SYSTEM);
    }
}
