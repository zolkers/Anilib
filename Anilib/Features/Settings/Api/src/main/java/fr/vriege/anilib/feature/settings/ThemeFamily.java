package fr.vriege.anilib.feature.settings;

public enum ThemeFamily {
    MATERIAL,
    TONAL,
    AMOLED;

    public ThemeFamily next() {
        ThemeFamily[] families = values();
        return families[(ordinal() + 1) % families.length];
    }
}
