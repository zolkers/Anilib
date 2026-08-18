package fr.vriege.anilib.feature.settings;

public enum LanguagePack {
    SYSTEM,
    ENGLISH,
    FRENCH,
    GERMAN,
    SPANISH,
    JAPANESE;

    public LanguagePack next() {
        LanguagePack[] packs = values();
        return packs[(ordinal() + 1) % packs.length];
    }
}
