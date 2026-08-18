package fr.vriege.anilib.feature.settings;

public enum StartScreen {
    LIBRARY,
    UPDATES,
    HISTORY,
    BROWSE,
    MORE;

    public StartScreen next() {
        StartScreen[] screens = values();
        return screens[(ordinal() + 1) % screens.length];
    }
}
