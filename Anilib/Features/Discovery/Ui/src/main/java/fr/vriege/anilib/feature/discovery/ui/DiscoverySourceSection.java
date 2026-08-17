package fr.vriege.anilib.feature.discovery.ui;

import fr.vriege.anilib.feature.source.SourceDescriptor;
import fr.vriege.anilib.foundation.validation.Preconditions;

import java.util.List;

/** Language header and rows matching Aniyomi's source list grouping. */
public record DiscoverySourceSection(String languageTag, List<SourceDescriptor> sources) {
    public DiscoverySourceSection {
        languageTag = Preconditions.requireNonBlank(languageTag, "languageTag");
        sources = List.copyOf(Preconditions.requireNonNull(sources, "sources"));
        if (sources.isEmpty()) {
            throw new IllegalArgumentException("sources must not be empty");
        }
    }
}
