package fr.vriege.anilib.feature.settings;

public enum TypographyScale {
    COMPACT(0.9f),
    STANDARD(1.0f),
    LARGE(1.15f);

    private final float multiplier;

    TypographyScale(float multiplier) {
        this.multiplier = multiplier;
    }

    public float multiplier() {
        return multiplier;
    }

    public TypographyScale next() {
        TypographyScale[] scales = values();
        return scales[(ordinal() + 1) % scales.length];
    }
}
