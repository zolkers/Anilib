package fr.vriege.anilib.feature.settings;

public enum NavigationStyle {
    ADAPTIVE,
    BOTTOM_BAR,
    NAVIGATION_RAIL;

    public NavigationStyle next() {
        NavigationStyle[] styles = values();
        return styles[(ordinal() + 1) % styles.length];
    }
}
