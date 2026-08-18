package fr.vriege.anilib.feature.extensionrepository;

import fr.vriege.anilib.foundation.validation.Preconditions;

import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

public record ExtensionBrowsePreferences(
        Set<String> enabledLanguages,
        Set<String> pinnedPackages) {
    public ExtensionBrowsePreferences {
        enabledLanguages = Preconditions.requireNonNull(enabledLanguages, "enabledLanguages").stream()
                .map(ExtensionBrowsePreferences::language)
                .collect(Collectors.toUnmodifiableSet());
        pinnedPackages = Preconditions.requireNonNull(pinnedPackages, "pinnedPackages").stream()
                .map(ExtensionPackageIdentifiers::requireValid)
                .collect(Collectors.toUnmodifiableSet());
    }

    public static ExtensionBrowsePreferences defaults() {
        return new ExtensionBrowsePreferences(Set.of(), Set.of());
    }

    private static String language(String value) {
        return Preconditions.requireNonBlank(value, "language")
                .replace('_', '-')
                .toLowerCase(Locale.ROOT);
    }
}
