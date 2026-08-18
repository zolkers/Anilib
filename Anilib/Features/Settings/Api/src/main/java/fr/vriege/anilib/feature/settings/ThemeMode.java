package fr.vriege.anilib.feature.settings;

public enum ThemeMode {
    SYSTEM,
    LIGHT,
    DARK;

    public ThemeMode next() {
        ThemeMode[] modes = values();
        return modes[(ordinal() + 1) % modes.length];
    }
}
