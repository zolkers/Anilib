package fr.vriege.anilib.feature.downloads;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public record AutomaticDownloadPolicy(
        boolean enabled,
        boolean favoritesOnly,
        boolean includeUncategorized,
        int defaultEpisodeLimit,
        int defaultChapterLimit,
        DownloadCleanupPolicy cleanupPolicy,
        int retainedCompletedPerTitle,
        List<AutomaticDownloadCategoryRule> categoryRules) {
    public AutomaticDownloadPolicy {
        validateLimit(defaultEpisodeLimit, "defaultEpisodeLimit");
        validateLimit(defaultChapterLimit, "defaultChapterLimit");
        cleanupPolicy = Objects.requireNonNull(cleanupPolicy, "cleanupPolicy must not be null");
        if (retainedCompletedPerTitle < 1 || retainedCompletedPerTitle > 100) {
            throw new IllegalArgumentException("retainedCompletedPerTitle must be between 1 and 100");
        }
        categoryRules = List.copyOf(categoryRules);
        Set<String> categories = new HashSet<>();
        for (AutomaticDownloadCategoryRule rule : categoryRules) {
            Objects.requireNonNull(rule, "categoryRules must not contain null values");
            if (!categories.add(rule.category())) {
                throw new IllegalArgumentException("categoryRules must not contain duplicate categories");
            }
        }
    }

    public static AutomaticDownloadPolicy disabled() {
        return new AutomaticDownloadPolicy(
                false,
                false,
                false,
                1,
                1,
                DownloadCleanupPolicy.KEEP_ALL,
                3,
                List.of());
    }

    private static void validateLimit(int value, String name) {
        if (value < 0 || value > 100) {
            throw new IllegalArgumentException(name + " must be between 0 and 100");
        }
    }
}
