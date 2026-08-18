package fr.vriege.anilib.feature.discovery;

import fr.vriege.anilib.feature.source.SourceContentKind;
import fr.vriege.anilib.feature.source.SourceId;
import fr.vriege.anilib.foundation.validation.Preconditions;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public record DiscoveryBrowsePreferences(
        Map<SourceContentKind, Set<String>> enabledLanguages,
        Set<SourceId> pinnedSources) {
    public DiscoveryBrowsePreferences {
        Map<SourceContentKind, Set<String>> normalized = new EnumMap<>(SourceContentKind.class);
        Preconditions.requireNonNull(enabledLanguages, "enabledLanguages").forEach((kind, languages) ->
                normalized.put(
                        Preconditions.requireNonNull(kind, "contentKind"),
                        Preconditions.requireNonNull(languages, "languages").stream()
                                .map(DiscoveryBrowsePreferences::language)
                                .collect(Collectors.toUnmodifiableSet())));
        enabledLanguages = Map.copyOf(normalized);
        pinnedSources = Set.copyOf(Preconditions.requireNonNull(pinnedSources, "pinnedSources"));
    }

    public static DiscoveryBrowsePreferences defaults() {
        return new DiscoveryBrowsePreferences(Map.of(), Set.of());
    }

    public Set<String> enabledLanguages(SourceContentKind contentKind) {
        return enabledLanguages.getOrDefault(
                Preconditions.requireNonNull(contentKind, "contentKind"),
                Set.of());
    }

    private static String language(String value) {
        return Preconditions.requireNonBlank(value, "language")
                .replace('_', '-')
                .toLowerCase(Locale.ROOT);
    }
}
