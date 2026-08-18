package fr.vriege.anilib.feature.reader;

import java.util.Objects;

public record ReaderDisplayPreferences(
        ReaderScaleMode scaleMode,
        boolean cropBorders,
        boolean splitPages,
        ReaderRotation rotation,
        boolean dualPage,
        int webtoonSpacingDp) {
    public ReaderDisplayPreferences {
        Objects.requireNonNull(scaleMode, "scaleMode must not be null");
        Objects.requireNonNull(rotation, "rotation must not be null");
        if (webtoonSpacingDp < 0 || webtoonSpacingDp > 96) {
            throw new IllegalArgumentException("webtoonSpacingDp must be between 0 and 96");
        }
        if (splitPages && dualPage) {
            throw new IllegalArgumentException("splitPages and dualPage are mutually exclusive");
        }
    }

    public static ReaderDisplayPreferences defaults() {
        return new ReaderDisplayPreferences(
                ReaderScaleMode.FIT,
                false,
                false,
                ReaderRotation.NONE,
                false,
                0);
    }
}
