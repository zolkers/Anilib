package fr.vriege.anilib.feature.reader;

import java.util.Objects;

public record ReaderInteractionPreferences(
        ReaderInteractionAction leftTap,
        ReaderInteractionAction centerTap,
        ReaderInteractionAction rightTap,
        ReaderInteractionAction topTap,
        ReaderInteractionAction bottomTap,
        ReaderInteractionAction swipeLeft,
        ReaderInteractionAction swipeRight,
        ReaderInteractionAction swipeUp,
        ReaderInteractionAction swipeDown,
        ReaderInteractionAction doubleTap,
        ReaderInteractionAction longPress) {
    public ReaderInteractionPreferences {
        Objects.requireNonNull(leftTap, "leftTap must not be null");
        Objects.requireNonNull(centerTap, "centerTap must not be null");
        Objects.requireNonNull(rightTap, "rightTap must not be null");
        Objects.requireNonNull(topTap, "topTap must not be null");
        Objects.requireNonNull(bottomTap, "bottomTap must not be null");
        Objects.requireNonNull(swipeLeft, "swipeLeft must not be null");
        Objects.requireNonNull(swipeRight, "swipeRight must not be null");
        Objects.requireNonNull(swipeUp, "swipeUp must not be null");
        Objects.requireNonNull(swipeDown, "swipeDown must not be null");
        Objects.requireNonNull(doubleTap, "doubleTap must not be null");
        Objects.requireNonNull(longPress, "longPress must not be null");
    }

    public static ReaderInteractionPreferences defaults() {
        return new ReaderInteractionPreferences(
                ReaderInteractionAction.PREVIOUS_PAGE,
                ReaderInteractionAction.TOGGLE_CONTROLS,
                ReaderInteractionAction.NEXT_PAGE,
                ReaderInteractionAction.PREVIOUS_PAGE,
                ReaderInteractionAction.NEXT_PAGE,
                ReaderInteractionAction.NEXT_PAGE,
                ReaderInteractionAction.PREVIOUS_PAGE,
                ReaderInteractionAction.NEXT_PAGE,
                ReaderInteractionAction.PREVIOUS_PAGE,
                ReaderInteractionAction.TOGGLE_ZOOM,
                ReaderInteractionAction.OPEN_MENU);
    }
}
