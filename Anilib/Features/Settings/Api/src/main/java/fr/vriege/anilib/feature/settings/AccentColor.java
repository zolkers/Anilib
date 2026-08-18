package fr.vriege.anilib.feature.settings;

public enum AccentColor {
    DEFAULT,
    OCEAN,
    FOREST,
    SAKURA;

    public AccentColor next() {
        AccentColor[] colors = values();
        return colors[(ordinal() + 1) % colors.length];
    }
}
