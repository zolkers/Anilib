package fr.vriege.anilib.feature.discovery.ui;

import fr.vriege.anilib.feature.discovery.DiscoveryService;
import fr.vriege.anilib.feature.discovery.SourcePreferenceSnapshot;
import fr.vriege.anilib.feature.library.LibraryItemId;
import fr.vriege.anilib.feature.source.SourceCatalogueItem;
import fr.vriege.anilib.feature.source.SourceContentKind;
import fr.vriege.anilib.feature.source.SourceFilterDefinition;
import fr.vriege.anilib.feature.source.SourceFilterValue;
import fr.vriege.anilib.feature.source.SourceId;
import fr.vriege.anilib.feature.source.SourceListing;
import fr.vriege.anilib.feature.source.SourcePage;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Deterministic presentation adapter over the Discovery service. */
public final class DefaultDiscoveryPresentation implements DiscoveryPresentation {
    private final DiscoveryService service;

    public DefaultDiscoveryPresentation(DiscoveryService service) {
        this.service = Objects.requireNonNull(service, "service must not be null");
    }

    @Override
    public List<DiscoverySourceSection> sourceSections(SourceContentKind contentKind) {
        Map<String, List<fr.vriege.anilib.feature.source.SourceDescriptor>> grouped = new LinkedHashMap<>();
        service.sources(contentKind).forEach(source ->
                grouped.computeIfAbsent(source.languageTag(), ignored -> new ArrayList<>()).add(source));
        return grouped.entrySet().stream()
                .map(entry -> new DiscoverySourceSection(entry.getKey(), entry.getValue()))
                .toList();
    }

    @Override
    public boolean supportsLatest(SourceId sourceId) {
        return service.supportsLatest(sourceId);
    }

    @Override
    public SourcePage browse(
            SourceId sourceId,
            SourceListing listing,
            int page,
            int pageSize,
            List<SourceFilterValue> filters) {
        return service.browse(sourceId, listing, page, pageSize, filters);
    }

    @Override
    public SourcePage search(
            SourceId sourceId,
            String query,
            int page,
            int pageSize,
            List<SourceFilterValue> filters) {
        return service.search(sourceId, query, page, pageSize, filters);
    }

    @Override
    public Map<SourceId, SourcePage> globalSearch(SourceContentKind contentKind, String query, int pageSize) {
        return service.globalSearch(contentKind, query, pageSize);
    }

    @Override
    public List<SourceFilterDefinition> filters(SourceId sourceId) {
        return service.filters(sourceId);
    }

    @Override
    public List<SourcePreferenceSnapshot> preferences(SourceId sourceId) {
        return service.preferences(sourceId);
    }

    @Override
    public void setPreference(SourceId sourceId, String preferenceId, String value) {
        service.setPreference(sourceId, preferenceId, value);
    }

    @Override
    public LibraryItemId addToLibrary(SourceCatalogueItem item) {
        return service.addToLibrary(item);
    }

    @Override
    public List<SourceCatalogueItem> migrationCandidates(
            LibraryItemId libraryItemId,
            SourceId targetSourceId,
            int limit) {
        return service.migrationCandidates(libraryItemId, targetSourceId, limit);
    }

    @Override
    public void migrate(LibraryItemId libraryItemId, SourceCatalogueItem target) {
        service.migrate(libraryItemId, target);
    }
}
