package fr.vriege.anilib.feature.discovery.runtime;

import fr.vriege.anilib.feature.discovery.DiscoveryService;
import fr.vriege.anilib.feature.discovery.SourcePreferenceSnapshot;
import fr.vriege.anilib.feature.library.LibraryCatalog;
import fr.vriege.anilib.feature.library.LibraryItem;
import fr.vriege.anilib.feature.library.LibraryItemId;
import fr.vriege.anilib.feature.library.LibraryOrigin;
import fr.vriege.anilib.feature.library.LibraryTitleMetadata;
import fr.vriege.anilib.feature.library.MediaKind;
import fr.vriege.anilib.feature.library.PublicationStatus;
import fr.vriege.anilib.feature.source.CatalogueSource;
import fr.vriege.anilib.feature.source.InstalledSourceExtension;
import fr.vriege.anilib.feature.source.SourceBrowseRequest;
import fr.vriege.anilib.feature.source.SourceCatalogueItem;
import fr.vriege.anilib.feature.source.SourceContentKind;
import fr.vriege.anilib.feature.source.SourceDescriptor;
import fr.vriege.anilib.feature.source.SourceFilterDefinition;
import fr.vriege.anilib.feature.source.SourceFilterType;
import fr.vriege.anilib.feature.source.SourceFilterValue;
import fr.vriege.anilib.feature.source.SourceId;
import fr.vriege.anilib.feature.source.SourceListing;
import fr.vriege.anilib.feature.source.SourcePage;
import fr.vriege.anilib.feature.source.SourcePreferenceDefinition;
import fr.vriege.anilib.feature.source.SourcePreferenceType;
import fr.vriege.anilib.feature.source.SourceRegistry;
import fr.vriege.anilib.feature.source.SourceSearchRequest;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Default cross-source discovery behavior shared by every platform. */
public final class DefaultDiscoveryService implements DiscoveryService {
    private final SourceRegistry registry;
    private final LibraryCatalog library;
    private final FileSourcePreferenceStore preferences;

    public DefaultDiscoveryService(SourceRegistry registry, LibraryCatalog library, Path preferenceFile) {
        this.registry = Objects.requireNonNull(registry, "registry must not be null");
        this.library = Objects.requireNonNull(library, "library must not be null");
        preferences = new FileSourcePreferenceStore(preferenceFile);
    }

    @Override
    public List<SourceDescriptor> sources(SourceContentKind contentKind) {
        Objects.requireNonNull(contentKind, "contentKind must not be null");
        return registry.sources().stream()
                .filter(CatalogueSource.class::isInstance)
                .map(source -> source.descriptor())
                .filter(descriptor -> descriptor.contentKinds().contains(contentKind))
                .sorted(java.util.Comparator.comparing(SourceDescriptor::languageTag)
                        .thenComparing(SourceDescriptor::displayName, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(SourceDescriptor::id))
                .toList();
    }

    @Override
    public List<InstalledSourceExtension> extensions(SourceContentKind contentKind) {
        Objects.requireNonNull(contentKind, "contentKind must not be null");
        return registry.extensions().stream()
                .filter(extension -> extension.source().contentKinds().contains(contentKind))
                .sorted(java.util.Comparator
                        .comparing((InstalledSourceExtension extension) -> extension.source().languageTag())
                        .thenComparing(
                                extension -> extension.manifest().component().displayName(),
                                String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(extension -> extension.source().id()))
                .toList();
    }

    @Override
    public boolean supportsLatest(SourceId sourceId) {
        return catalogue(sourceId).supportsLatest();
    }

    @Override
    public SourcePage browse(
            SourceId sourceId,
            SourceListing listing,
            int page,
            int pageSize,
            List<SourceFilterValue> filters) {
        CatalogueSource source = catalogue(sourceId);
        SourceBrowseRequest request = request(source, page, pageSize, filters);
        return switch (Objects.requireNonNull(listing, "listing must not be null")) {
            case POPULAR -> source.popular(request);
            case LATEST -> {
                if (!source.supportsLatest()) {
                    throw new IllegalArgumentException("Source does not support latest listings: " + sourceId);
                }
                yield source.latest(request);
            }
        };
    }

    @Override
    public SourcePage search(
            SourceId sourceId,
            String query,
            int page,
            int pageSize,
            List<SourceFilterValue> filters) {
        CatalogueSource source = catalogue(sourceId);
        return source.search(new SourceSearchRequest(query, request(source, page, pageSize, filters)));
    }

    @Override
    public Map<SourceId, SourcePage> globalSearch(
            SourceContentKind contentKind,
            String query,
            int pageSize) {
        Map<SourceId, SourcePage> results = new LinkedHashMap<>();
        for (SourceDescriptor descriptor : sources(contentKind)) {
            results.put(descriptor.id(), search(descriptor.id(), query, 1, pageSize, List.of()));
        }
        return Collections.unmodifiableMap(results);
    }

    @Override
    public List<SourceFilterDefinition> filters(SourceId sourceId) {
        List<SourceFilterDefinition> definitions = List.copyOf(catalogue(sourceId).filters());
        requireUnique(definitions.stream().map(SourceFilterDefinition::id).toList(), "filter");
        return definitions;
    }

    @Override
    public List<SourcePreferenceSnapshot> preferences(SourceId sourceId) {
        CatalogueSource source = catalogue(sourceId);
        List<SourcePreferenceDefinition> definitions = List.copyOf(source.preferences());
        requireUnique(definitions.stream().map(SourcePreferenceDefinition::id).toList(), "preference");
        return definitions.stream()
                .map(definition -> new SourcePreferenceSnapshot(
                        definition,
                        preferences.get(sourceId, definition.id(), definition.defaultValue())))
                .toList();
    }

    @Override
    public void setPreference(SourceId sourceId, String preferenceId, String value) {
        SourcePreferenceDefinition definition = catalogue(sourceId).preferences().stream()
                .filter(candidate -> candidate.id().equals(preferenceId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown source preference: " + preferenceId));
        validatePreference(definition, value);
        preferences.set(sourceId, preferenceId, value);
    }

    @Override
    public LibraryItemId addToLibrary(SourceCatalogueItem item) {
        Objects.requireNonNull(item, "item must not be null");
        catalogue(item.id().sourceId());
        LibraryOrigin origin = origin(item);
        LibraryItem existing = library.snapshot().stream()
                .filter(candidate -> candidate.origin().filter(origin::equals).isPresent())
                .findFirst()
                .orElse(null);
        if (existing != null) {
            return existing.id();
        }
        LibraryItem created = LibraryItem.create(item.title(), mediaKind(item.contentKind()))
                .withMetadata(metadata(item))
                .withOrigin(origin);
        library.save(created);
        return created.id();
    }

    @Override
    public List<SourceCatalogueItem> migrationCandidates(
            LibraryItemId libraryItemId,
            SourceId targetSourceId,
            int limit) {
        if (limit < 1 || limit > 100) {
            throw new IllegalArgumentException("limit must be between 1 and 100");
        }
        LibraryItem item = library.find(libraryItemId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown library item: " + libraryItemId));
        return search(targetSourceId, item.title(), 1, limit, List.of()).items().stream()
                .filter(candidate -> mediaKind(candidate.contentKind()) == item.kind())
                .toList();
    }

    @Override
    public void migrate(LibraryItemId libraryItemId, SourceCatalogueItem target) {
        Objects.requireNonNull(target, "target must not be null");
        LibraryItem current = library.find(libraryItemId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown library item: " + libraryItemId));
        if (mediaKind(target.contentKind()) != current.kind()) {
            throw new IllegalArgumentException("Migration target must keep the library media kind");
        }
        catalogue(target.id().sourceId());
        library.save(current.migratedTo(target.title(), origin(target), metadata(target)));
    }

    private SourceBrowseRequest request(
            CatalogueSource source,
            int page,
            int pageSize,
            List<SourceFilterValue> filterValues) {
        List<SourceFilterValue> validated = validateFilters(source, filterValues);
        Map<String, String> preferenceValues = new LinkedHashMap<>();
        for (SourcePreferenceDefinition definition : source.preferences()) {
            preferenceValues.put(
                    definition.id(),
                    preferences.get(source.descriptor().id(), definition.id(), definition.defaultValue()));
        }
        return new SourceBrowseRequest(page, pageSize, validated, preferenceValues);
    }

    private static List<SourceFilterValue> validateFilters(
            CatalogueSource source,
            List<SourceFilterValue> values) {
        Objects.requireNonNull(values, "filters must not be null");
        Map<String, SourceFilterDefinition> definitions = new LinkedHashMap<>();
        for (SourceFilterDefinition definition : source.filters()) {
            if (definitions.putIfAbsent(definition.id(), definition) != null) {
                throw new IllegalStateException("Duplicate source filter id: " + definition.id());
            }
        }
        HashSet<String> seen = new HashSet<>();
        List<SourceFilterValue> result = new ArrayList<>();
        for (SourceFilterValue value : values) {
            if (!seen.add(value.filterId())) {
                throw new IllegalArgumentException("Duplicate filter value: " + value.filterId());
            }
            SourceFilterDefinition definition = definitions.get(value.filterId());
            if (definition == null || definition.type() == SourceFilterType.HEADER
                    || definition.type() == SourceFilterType.SEPARATOR) {
                throw new IllegalArgumentException("Unknown or stateless source filter: " + value.filterId());
            }
            validateFilter(definition, value.value());
            result.add(value);
        }
        return List.copyOf(result);
    }

    private static void validateFilter(SourceFilterDefinition definition, String value) {
        switch (definition.type()) {
            case CHECKBOX -> requireOneOf(value, List.of("true", "false"), "checkbox filter");
            case TRI_STATE -> requireOneOf(
                    value,
                    List.of("ignore", "include", "exclude"),
                    "tri-state filter");
            case SELECT, SORT -> requireOneOf(value, definition.options(), "option filter");
            case HEADER, SEPARATOR -> throw new IllegalArgumentException("Filter is stateless");
            case TEXT -> {
                // Any text is valid.
            }
        }
    }

    private static void validatePreference(SourcePreferenceDefinition definition, String value) {
        Objects.requireNonNull(value, "value must not be null");
        if (definition.type() == SourcePreferenceType.SWITCH) {
            requireOneOf(value, List.of("true", "false"), "switch preference");
        } else if (definition.type() == SourcePreferenceType.SELECT) {
            requireOneOf(value, definition.options(), "select preference");
        }
    }

    private CatalogueSource catalogue(SourceId sourceId) {
        return registry.find(Objects.requireNonNull(sourceId, "sourceId must not be null"))
                .filter(CatalogueSource.class::isInstance)
                .map(CatalogueSource.class::cast)
                .orElseThrow(() -> new IllegalArgumentException("Source is not browseable: " + sourceId));
    }

    private static void requireUnique(List<String> values, String label) {
        if (new HashSet<>(values).size() != values.size()) {
            throw new IllegalStateException("Duplicate source " + label + " id");
        }
    }

    private static void requireOneOf(String value, List<String> allowed, String label) {
        if (!allowed.contains(value)) {
            throw new IllegalArgumentException("Invalid " + label + " value: " + value);
        }
    }

    private static MediaKind mediaKind(SourceContentKind kind) {
        return switch (kind) {
            case ANIME -> MediaKind.ANIME;
            case MANGA -> MediaKind.MANGA;
        };
    }

    private static LibraryOrigin origin(SourceCatalogueItem item) {
        return new LibraryOrigin(item.id().sourceId().toString(), item.id().value());
    }

    private static LibraryTitleMetadata metadata(SourceCatalogueItem item) {
        return new LibraryTitleMetadata(
                item.description(),
                List.of(),
                List.of(),
                PublicationStatus.UNKNOWN);
    }
}
