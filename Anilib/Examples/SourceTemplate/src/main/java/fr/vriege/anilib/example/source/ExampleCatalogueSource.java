package fr.vriege.anilib.example.source;

import fr.vriege.anilib.feature.source.CatalogueSource;
import fr.vriege.anilib.feature.source.SourceApiVersion;
import fr.vriege.anilib.feature.source.SourceBrowseRequest;
import fr.vriege.anilib.feature.source.SourceCatalogueItem;
import fr.vriege.anilib.feature.source.SourceCatalogueItemId;
import fr.vriege.anilib.feature.source.SourceContentKind;
import fr.vriege.anilib.feature.source.SourceDescriptor;
import fr.vriege.anilib.feature.source.SourceId;
import fr.vriege.anilib.feature.source.SourcePage;
import fr.vriege.anilib.feature.source.SourceSearchRequest;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/** Small functional catalogue used to validate one source on Android and desktop. */
final class ExampleCatalogueSource implements CatalogueSource {
    private static final SourceId ID = SourceId.of("example.catalogue");
    private static final List<SourceCatalogueItem> ITEMS = List.of(
            item("test1", "Test n1", "aaaaaaaaaaaaaaaaaaaaaaa"),
            item("test2", "Test n2", "bbbbbbbbbbbbbbbbbbbbbbb"),
            item("test3", "Test n3", "Tcccccccccccccccccccccc"));

    @Override
    public SourceDescriptor descriptor() {
        return new SourceDescriptor(
                ID,
                "Anilib Example Catalogue",
                "1.0.0",
                "en",
                Set.of(SourceContentKind.MANGA),
                new SourceApiVersion(1, 6));
    }

    @Override
    public SourcePage popular(SourceBrowseRequest request) {
        return page(ITEMS, request);
    }

    @Override
    public SourcePage search(SourceSearchRequest request) {
        String query = request.query().toLowerCase(Locale.ROOT);
        List<SourceCatalogueItem> matches = ITEMS.stream()
                .filter(item -> item.title().toLowerCase(Locale.ROOT).contains(query))
                .toList();
        return page(matches, request.browseRequest());
    }

    private static SourcePage page(List<SourceCatalogueItem> items, SourceBrowseRequest request) {
        int from = Math.min((request.page() - 1) * request.pageSize(), items.size());
        int to = Math.min(from + request.pageSize(), items.size());
        return new SourcePage(items.subList(from, to), to < items.size());
    }

    private static SourceCatalogueItem item(String id, String title, String description) {
        return new SourceCatalogueItem(
                new SourceCatalogueItemId(ID, id),
                title,
                description,
                Optional.empty(),
                SourceContentKind.MANGA);
    }
}
