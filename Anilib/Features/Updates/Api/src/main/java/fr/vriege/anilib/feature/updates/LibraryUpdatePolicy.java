package fr.vriege.anilib.feature.updates;

import fr.vriege.anilib.feature.library.LibraryItemId;

import java.util.Objects;
import java.util.Set;

public record LibraryUpdatePolicy(
        UpdateInterval interval,
        boolean favoritesOnly,
        boolean skipCompleted,
        boolean skipNotStarted,
        Set<String> includedCategories,
        Set<String> excludedCategories,
        Set<LibraryItemId> includedTitles,
        Set<LibraryItemId> excludedTitles) {
    public LibraryUpdatePolicy {
        Objects.requireNonNull(interval, "interval must not be null");
        includedCategories = validatedCategories(includedCategories, "includedCategories");
        excludedCategories = validatedCategories(excludedCategories, "excludedCategories");
        includedTitles = Set.copyOf(Objects.requireNonNull(includedTitles, "includedTitles must not be null"));
        excludedTitles = Set.copyOf(Objects.requireNonNull(excludedTitles, "excludedTitles must not be null"));
        if (!java.util.Collections.disjoint(includedTitles, excludedTitles)) {
            throw new IllegalArgumentException("includedTitles and excludedTitles must not overlap");
        }
    }

    public LibraryUpdatePolicy(
            UpdateInterval interval,
            boolean favoritesOnly,
            boolean skipCompleted,
            boolean skipNotStarted,
            Set<String> includedCategories,
            Set<String> excludedCategories) {
        this(
                interval,
                favoritesOnly,
                skipCompleted,
                skipNotStarted,
                includedCategories,
                excludedCategories,
                Set.of(),
                Set.of());
    }

    public static LibraryUpdatePolicy defaults() {
        return new LibraryUpdatePolicy(
                UpdateInterval.DAILY,
                false,
                false,
                false,
                Set.of(),
                Set.of(),
                Set.of(),
                Set.of());
    }

    private static Set<String> validatedCategories(Set<String> categories, String name) {
        Set<String> copy = Set.copyOf(Objects.requireNonNull(categories, name + " must not be null"));
        if (copy.stream().anyMatch(String::isBlank)) {
            throw new IllegalArgumentException(name + " must not contain blank values");
        }
        return copy;
    }
}
