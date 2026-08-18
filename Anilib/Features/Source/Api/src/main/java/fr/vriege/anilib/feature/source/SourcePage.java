package fr.vriege.anilib.feature.source;

import fr.vriege.anilib.foundation.validation.Preconditions;

import java.util.HashSet;
import java.util.List;

public record SourcePage(List<SourceCatalogueItem> items, boolean hasNextPage) {
    public SourcePage {
        items = List.copyOf(Preconditions.requireNonNull(items, "items"));
        if (new HashSet<>(items.stream().map(SourceCatalogueItem::id).toList()).size() != items.size()) {
            throw new IllegalArgumentException("items must not contain duplicate identities");
        }
    }
}
