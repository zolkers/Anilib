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

    /**
     * Repairs a degenerate horizontal layout where both physical edges turn the same way.
     * Direction mirroring is applied later by the viewer, so the stored baseline is always
     * left=previous and right=next.
     */
    public ReaderInteractionPreferences withUsableHorizontalTaps() {
        boolean leftTurnsPage = leftTap == ReaderInteractionAction.PREVIOUS_PAGE
                || leftTap == ReaderInteractionAction.NEXT_PAGE;
        if (!leftTurnsPage || leftTap != rightTap) {
            return this;
        }
        return new ReaderInteractionPreferences(
                ReaderInteractionAction.PREVIOUS_PAGE,
                centerTap,
                ReaderInteractionAction.NEXT_PAGE,
                topTap,
                bottomTap,
                swipeLeft,
                swipeRight,
                swipeUp,
                swipeDown,
                doubleTap,
                longPress);
    }
}
