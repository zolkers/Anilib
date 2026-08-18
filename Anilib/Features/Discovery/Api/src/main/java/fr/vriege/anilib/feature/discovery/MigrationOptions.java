package fr.vriege.anilib.feature.discovery;

public record MigrationOptions(
        boolean preserveOriginalTitle,
        boolean seasonalAnimeSearch) {
    public static MigrationOptions defaults() {
        return new MigrationOptions(false, false);
    }
}
