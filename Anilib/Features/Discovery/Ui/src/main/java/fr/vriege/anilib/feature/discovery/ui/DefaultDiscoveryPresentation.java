package fr.vriege.anilib.feature.discovery.ui;

import fr.vriege.anilib.feature.discovery.DiscoveryBrowsePreferenceStore;
import fr.vriege.anilib.feature.discovery.DiscoveryBrowsePreferences;
import fr.vriege.anilib.feature.discovery.DiscoveryCatalogueDisplayMode;
import fr.vriege.anilib.feature.discovery.DiscoveryService;
import fr.vriege.anilib.feature.discovery.MigrationOptions;
import fr.vriege.anilib.feature.discovery.SourcePreferenceSnapshot;
import fr.vriege.anilib.feature.library.LibraryItemId;
import fr.vriege.anilib.feature.source.SourceCatalogueItem;
import fr.vriege.anilib.feature.source.SourceCatalogueItemId;
import fr.vriege.anilib.feature.source.SourceContentKind;
import fr.vriege.anilib.feature.source.SourceDescriptor;
import fr.vriege.anilib.feature.source.InstalledSourceExtension;
import fr.vriege.anilib.feature.source.SourceFilterDefinition;
import fr.vriege.anilib.feature.source.SourceFilterValue;
import fr.vriege.anilib.feature.source.SourceId;
import fr.vriege.anilib.feature.source.SourceListing;
import fr.vriege.anilib.feature.source.SourcePage;
import fr.vriege.anilib.feature.source.SourceWebPage;
import fr.vriege.anilib.feature.source.SourceTitleDetails;
import fr.vriege.anilib.feature.source.SourceContentUnit;
import fr.vriege.anilib.feature.source.SourceEpisode;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class DefaultDiscoveryPresentation implements DiscoveryPresentation {
    private final DiscoveryService service;
    private final DiscoveryBrowsePreferenceStore browsePreferences;

    public DefaultDiscoveryPresentation(
            DiscoveryService service,
            DiscoveryBrowsePreferenceStore browsePreferences) {
        this.service = Objects.requireNonNull(service, "service must not be null");
        this.browsePreferences = Objects.requireNonNull(browsePreferences, "browsePreferences must not be null");
    }

    @Override
    public List<DiscoverySourceSection> sourceSections(SourceContentKind contentKind) {
        DiscoveryBrowsePreferences preferences = browsePreferences.snapshot();
        Set<String> enabledLanguages = effectiveLanguages(contentKind, preferences);
        Set<SourceId> pinned = preferences.pinnedSources();
        Map<String, List<SourceDescriptor>> grouped = new LinkedHashMap<>();
        service.sources(contentKind).stream()
                .filter(source -> enabledLanguages.contains(source.languageTag()))
                .filter(source -> !preferences.disabledSources().contains(source.id()))
                .forEach(source ->
                grouped.computeIfAbsent(source.languageTag(), ignored -> new ArrayList<>()).add(source));
        return grouped.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> new DiscoverySourceSection(
                        entry.getKey(),
                        entry.getValue().stream()
                                .sorted(Comparator
                                        .comparing((SourceDescriptor source) -> !pinned.contains(source.id()))
                                        .thenComparing(
                                                SourceDescriptor::displayName,
                                                String.CASE_INSENSITIVE_ORDER)
                                        .thenComparing(source -> source.id().toString()))
                                .toList()))
                .toList();
    }

    @Override
    public Optional<SourceDescriptor> source(SourceId sourceId) {
        return service.source(sourceId);
    }

    @Override
    public List<String> availableSourceLanguages(SourceContentKind contentKind) {
        return service.sources(contentKind).stream()
                .map(SourceDescriptor::languageTag)
                .distinct()
                .sorted()
                .toList();
    }

    @Override
    public Set<String> enabledSourceLanguages(SourceContentKind contentKind) {
        return effectiveLanguages(contentKind, browsePreferences.snapshot());
    }

    @Override
    public Set<SourceId> pinnedSources() {
        return browsePreferences.snapshot().pinnedSources();
    }

    @Override
    public boolean sourceEnabled(SourceId sourceId) {
        SourceId id = Objects.requireNonNull(sourceId, "sourceId must not be null");
        return !browsePreferences.snapshot().disabledSources().contains(id);
    }

    @Override
    public void setSourceLanguageEnabled(
            SourceContentKind contentKind,
            String languageTag,
            boolean enabled) {
        SourceContentKind kind = Objects.requireNonNull(contentKind, "contentKind must not be null");
        String language = normalizeLanguage(languageTag);
        List<String> available = availableSourceLanguages(kind);
        if (!available.contains(language)) {
            throw new IllegalArgumentException("Unknown source language: " + language);
        }
        DiscoveryBrowsePreferences current = browsePreferences.snapshot();
        Set<String> selected = new LinkedHashSet<>(effectiveLanguages(kind, current));
        if (enabled) {
            selected.add(language);
        } else {
            if (selected.size() == 1 && selected.contains(language)) {
                throw new IllegalArgumentException("At least one source language must remain enabled");
            }
            selected.remove(language);
        }
        Map<SourceContentKind, Set<String>> languages = new EnumMap<>(SourceContentKind.class);
        languages.putAll(current.enabledLanguages());
        languages.put(kind, Set.copyOf(selected));
        browsePreferences.save(new DiscoveryBrowsePreferences(
                languages,
                current.pinnedSources(),
                current.catalogueDisplayModes(),
                current.disabledSources()));
    }

    @Override
    public void setSourcePinned(SourceId sourceId, boolean pinned) {
        SourceId id = Objects.requireNonNull(sourceId, "sourceId must not be null");
        DiscoveryBrowsePreferences current = browsePreferences.snapshot();
        Set<SourceId> sources = new LinkedHashSet<>(current.pinnedSources());
        if (pinned) {
            sources.add(id);
        } else {
            sources.remove(id);
        }
        browsePreferences.save(new DiscoveryBrowsePreferences(
                current.enabledLanguages(),
                sources,
                current.catalogueDisplayModes(),
                current.disabledSources()));
    }

    @Override
    public void setSourceEnabled(SourceId sourceId, boolean enabled) {
        SourceId id = Objects.requireNonNull(sourceId, "sourceId must not be null");
        if (service.source(id).isEmpty()) {
            throw new IllegalArgumentException("Unknown source: " + id);
        }
        DiscoveryBrowsePreferences current = browsePreferences.snapshot();
        Set<SourceId> disabled = new LinkedHashSet<>(current.disabledSources());
        if (enabled) {
            disabled.remove(id);
        } else {
            disabled.add(id);
        }
        browsePreferences.save(new DiscoveryBrowsePreferences(
                current.enabledLanguages(),
                current.pinnedSources(),
                current.catalogueDisplayModes(),
                disabled));
    }

    @Override
    public DiscoveryCatalogueDisplayMode catalogueDisplayMode(SourceId sourceId) {
        return browsePreferences.snapshot().catalogueDisplayMode(sourceId);
    }

    @Override
    public void setCatalogueDisplayMode(SourceId sourceId, DiscoveryCatalogueDisplayMode displayMode) {
        SourceId id = Objects.requireNonNull(sourceId, "sourceId must not be null");
        if (service.source(id).isEmpty()) {
            throw new IllegalArgumentException("Unknown source: " + id);
        }
        DiscoveryCatalogueDisplayMode mode = Objects.requireNonNull(
                displayMode,
                "displayMode must not be null");
        DiscoveryBrowsePreferences current = browsePreferences.snapshot();
        Map<SourceId, DiscoveryCatalogueDisplayMode> modes = new LinkedHashMap<>(
                current.catalogueDisplayModes());
        if (mode == DiscoveryCatalogueDisplayMode.GRID) {
            modes.remove(id);
        } else {
            modes.put(id, mode);
        }
        browsePreferences.save(new DiscoveryBrowsePreferences(
                current.enabledLanguages(),
                current.pinnedSources(),
                modes,
                current.disabledSources()));
    }

    @Override
    public List<InstalledSourceExtension> extensions(SourceContentKind contentKind) {
        return service.extensions(contentKind);
    }

    @Override
    public boolean supportsLatest(SourceId sourceId) {
        return service.supportsLatest(sourceId);
    }

    @Override
    public boolean supportsRefresh(SourceId sourceId) {
        return service.supportsRefresh(sourceId);
    }

    @Override
    public void refresh(SourceId sourceId) {
        service.refresh(sourceId);
    }

    @Override
    public Optional<SourceWebPage> sourceWebPage(SourceId sourceId) {
        return service.sourceWebPage(sourceId);
    }

    @Override
    public Optional<SourceWebPage> titleWebPage(SourceCatalogueItemId itemId) {
        return service.titleWebPage(itemId);
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
        DiscoveryBrowsePreferences preferences = browsePreferences.snapshot();
        Set<String> enabledLanguages = effectiveLanguages(contentKind, preferences);
        Map<SourceId, SourcePage> visible = new LinkedHashMap<>();
        service.globalSearch(contentKind, query, pageSize).forEach((sourceId, page) ->
                service.source(sourceId)
                        .filter(source -> enabledLanguages.contains(source.languageTag()))
                        .filter(source -> !preferences.disabledSources().contains(source.id()))
                        .ifPresent(source -> visible.put(sourceId, page)));
        return Map.copyOf(visible);
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
    public SourceTitleDetails titleDetails(SourceCatalogueItem item) {
        return service.titleDetails(item);
    }

    @Override
    public List<SourceContentUnit> contentUnits(SourceCatalogueItemId itemId) {
        return service.contentUnits(itemId);
    }

    @Override
    public List<SourceEpisode> episodes(SourceCatalogueItemId itemId) {
        return service.episodes(itemId);
    }

    @Override
    public Optional<LibraryItemId> libraryItem(SourceCatalogueItemId itemId) {
        return service.libraryItem(itemId);
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
    public List<SourceCatalogueItem> migrationCandidates(
            LibraryItemId libraryItemId,
            SourceId targetSourceId,
            MigrationOptions options,
            int limit) {
        return service.migrationCandidates(libraryItemId, targetSourceId, options, limit);
    }

    @Override
    public void migrate(LibraryItemId libraryItemId, SourceCatalogueItem target) {
        service.migrate(libraryItemId, target);
    }

    @Override
    public void migrate(
            LibraryItemId libraryItemId,
            SourceCatalogueItem target,
            MigrationOptions options) {
        service.migrate(libraryItemId, target, options);
    }

    private Set<String> effectiveLanguages(
            SourceContentKind contentKind,
            DiscoveryBrowsePreferences preferences) {
        List<String> available = availableSourceLanguages(contentKind);
        Set<String> configured = preferences.enabledLanguages(contentKind);
        if (configured.isEmpty()) {
            return defaultLanguages(available);
        }
        Set<String> retained = new LinkedHashSet<>(configured);
        retained.retainAll(available);
        return retained.isEmpty() ? defaultLanguages(available) : Set.copyOf(retained);
    }

    private static Set<String> defaultLanguages(List<String> available) {
        Set<String> selected = new LinkedHashSet<>();
        addIfAvailable(selected, available, "und");
        addIfAvailable(selected, available, normalizeLanguage(Locale.getDefault().getLanguage()));
        addIfAvailable(selected, available, "en");
        if (selected.isEmpty() && !available.isEmpty()) {
            selected.add(available.getFirst());
        }
        return Set.copyOf(selected);
    }

    private static void addIfAvailable(Set<String> selected, List<String> available, String language) {
        if (!language.isEmpty() && available.contains(language)) {
            selected.add(language);
        }
    }

    private static String normalizeLanguage(String languageTag) {
        return Objects.requireNonNull(languageTag, "languageTag must not be null")
                .replace('_', '-')
                .toLowerCase(Locale.ROOT);
    }
}
