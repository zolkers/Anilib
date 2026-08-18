package fr.vriege.anilib.feature.downloads;

public record AutomaticDownloadCategoryRule(
        String category,
        int episodeLimit,
        int chapterLimit) {
    public AutomaticDownloadCategoryRule {
        if (category == null || category.isBlank() || !category.equals(category.strip())
                || category.length() > 200) {
            throw new IllegalArgumentException("category must be trimmed and contain 1 to 200 characters");
        }
        validateLimit(episodeLimit, "episodeLimit");
        validateLimit(chapterLimit, "chapterLimit");
    }

    private static void validateLimit(int value, String name) {
        if (value < 0 || value > 100) {
            throw new IllegalArgumentException(name + " must be between 0 and 100");
        }
    }
}
