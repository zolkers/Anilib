package fr.vriege.anilib.feature.library.ui;

import fr.vriege.anilib.feature.library.LibraryCatalog;
import fr.vriege.anilib.feature.library.LibraryCategory;
import fr.vriege.anilib.feature.library.LibraryCategoryUpdatePolicy;
import fr.vriege.anilib.feature.library.LibraryConfiguration;
import fr.vriege.anilib.feature.library.LibraryConfigurationSnapshot;
import fr.vriege.anilib.feature.library.LibraryDisplayDensity;
import fr.vriege.anilib.feature.library.LibraryDisplayMode;
import fr.vriege.anilib.feature.library.LibraryDisplayPreferences;
import fr.vriege.anilib.feature.library.LibraryHistoryEntry;
import fr.vriege.anilib.feature.library.LibraryItem;
import fr.vriege.anilib.feature.library.LibraryItemId;
import fr.vriege.anilib.feature.library.LibrarySort;
import fr.vriege.anilib.feature.library.LibraryTitleMetadata;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.Collections;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

public final class DefaultLibraryPresentation implements LibraryPresentation {
    private static final Comparator<String> TEXT_ORDER =
            String.CASE_INSENSITIVE_ORDER.thenComparing(Comparator.naturalOrder());
    private static final Comparator<LibraryHistoryRow> HISTORY_ORDER =
            Comparator.comparing(LibraryHistoryRow::openedAt).reversed()
                    .thenComparing(LibraryHistoryRow::title, TEXT_ORDER)
                    .thenComparing(LibraryHistoryRow::libraryItemId)
                    .thenComparing(LibraryHistoryRow::contentId);

    private final LibraryCatalog catalog;
    private final LibraryConfiguration configuration;

    public DefaultLibraryPresentation(
            LibraryCatalog catalog,
            LibraryConfiguration configuration) {
        this.catalog = Objects.requireNonNull(catalog, "catalog must not be null");
        this.configuration = Objects.requireNonNull(
                configuration,
                "configuration must not be null");
    }

    @Override
    public synchronized LibraryOverview library() {
        List<LibraryItem> items = catalog.snapshot();
        LibraryConfigurationSnapshot configured = configuration.snapshot();
        List<LibraryCategory> categories = effectiveCategories(items, configured);
        LibraryDisplayPreferences preferences = activePreferences(
                configured.displayPreferences(),
                categories);
        List<LibraryCard> cards = items.stream()
                .map(DefaultLibraryPresentation::card)
                .sorted(cardOrder(preferences.sort()))
                .toList();
        int favoriteCount = Math.toIntExact(items.stream().filter(LibraryItem::favorite).count());
        return new LibraryOverview(
                cards,
                categories.stream().map(LibraryCategory::name).toList(),
                categories,
                preferences,
                favoriteCount);
    }

    @Override
    public Optional<LibraryDetails> details(LibraryItemId id) {
        Objects.requireNonNull(id, "id must not be null");
        return catalog.find(id).map(DefaultLibraryPresentation::details);
    }

    @Override
    public LibraryHistory history() {
        List<LibraryHistoryRow> rows = catalog.snapshot().stream()
                .flatMap(item -> item.history().stream().map(entry -> historyRow(item, entry)))
                .sorted(HISTORY_ORDER)
                .toList();
        return new LibraryHistory(rows);
    }

    @Override
    public synchronized void removeHistoryEntry(
            LibraryItemId id,
            String contentId,
            Instant openedAt) {
        Objects.requireNonNull(id, "id must not be null");
        LibraryItem item = catalog.find(id)
                .orElseThrow(() -> new IllegalArgumentException("Unknown library title: " + id));
        catalog.save(item.withoutHistoryEntry(contentId, openedAt));
    }

    @Override
    public synchronized void setDisplayMode(LibraryDisplayMode mode) {
        updateActiveDisplay(Optional.of(mode), Optional.empty(), Optional.empty());
    }

    @Override
    public synchronized void setDisplayDensity(LibraryDisplayDensity density) {
        updateActiveDisplay(Optional.empty(), Optional.of(density), Optional.empty());
    }

    @Override
    public synchronized void setSort(LibrarySort sort) {
        updateActiveDisplay(Optional.empty(), Optional.empty(), Optional.of(sort));
    }

    @Override
    public synchronized void setDefaultCategory(Optional<String> category) {
        Objects.requireNonNull(category, "category must not be null");
        LibraryConfigurationSnapshot current = normalizedConfiguration();
        category.ifPresent(name -> requireCategory(current.categories(), name));
        LibraryDisplayPreferences preferences = current.displayPreferences();
        saveConfiguration(new LibraryConfigurationSnapshot(
                new LibraryDisplayPreferences(
                        preferences.mode(),
                        preferences.density(),
                        preferences.sort(),
                        category),
                current.categories()));
    }

    @Override
    public synchronized void createCategory(String name) {
        LibraryConfigurationSnapshot current = normalizedConfiguration();
        createCategory(LibraryCategory.defaults(name, current.displayPreferences()));
    }

    @Override
    public synchronized void createCategory(LibraryCategory category) {
        Objects.requireNonNull(category, "category must not be null");
        LibraryConfigurationSnapshot current = normalizedConfiguration();
        if (current.categories().stream().anyMatch(value -> value.name().equals(category.name()))) {
            throw new IllegalArgumentException("Library category already exists: " + category.name());
        }
        List<LibraryCategory> categories = new ArrayList<>(current.categories());
        categories.add(category);
        saveConfiguration(new LibraryConfigurationSnapshot(current.displayPreferences(), categories));
    }

    @Override
    public synchronized void renameCategory(String currentName, String nextName) {
        LibraryConfigurationSnapshot current = normalizedConfiguration();
        LibraryCategory category = requireCategory(current.categories(), currentName);
        replaceCategory(currentName, new LibraryCategory(
                nextName,
                category.displayMode(),
                category.density(),
                category.sort(),
                category.updatePolicy()));
    }

    @Override
    public synchronized void replaceCategory(String currentName, LibraryCategory replacement) {
        Objects.requireNonNull(replacement, "category must not be null");
        LibraryConfigurationSnapshot current = normalizedConfiguration();
        requireCategory(current.categories(), currentName);
        if (!currentName.equals(replacement.name())
                && current.categories().stream()
                .anyMatch(value -> value.name().equals(replacement.name()))) {
            throw new IllegalArgumentException(
                    "Library category already exists: " + replacement.name());
        }
        List<LibraryCategory> categories = replaceCategory(
                current.categories(),
                currentName,
                replacement);
        Optional<String> defaultCategory = current.displayPreferences().defaultCategory()
                .map(value -> value.equals(currentName) ? replacement.name() : value);
        LibraryDisplayPreferences preferences = withDefaultCategory(
                current.displayPreferences(),
                defaultCategory);
        List<LibraryItem> before = catalog.snapshot();
        List<LibraryItem> after = before.stream()
                .map(item -> renameCategory(item, currentName, replacement.name()))
                .toList();
        replaceItemsAndConfiguration(
                before,
                after,
                new LibraryConfigurationSnapshot(preferences, categories));
    }

    @Override
    public synchronized void moveCategory(String name, int targetIndex) {
        LibraryConfigurationSnapshot current = normalizedConfiguration();
        LibraryCategory category = requireCategory(current.categories(), name);
        if (targetIndex < 0 || targetIndex >= current.categories().size()) {
            throw new IllegalArgumentException("targetIndex is outside the category list");
        }
        List<LibraryCategory> categories = new ArrayList<>(current.categories());
        categories.remove(category);
        categories.add(targetIndex, category);
        saveConfiguration(new LibraryConfigurationSnapshot(current.displayPreferences(), categories));
    }

    @Override
    public synchronized void deleteCategory(String name) {
        LibraryConfigurationSnapshot current = normalizedConfiguration();
        requireCategory(current.categories(), name);
        List<LibraryCategory> categories = current.categories().stream()
                .filter(category -> !category.name().equals(name))
                .toList();
        Optional<String> defaultCategory = current.displayPreferences().defaultCategory()
                .filter(value -> !value.equals(name));
        LibraryDisplayPreferences preferences = withDefaultCategory(
                current.displayPreferences(),
                defaultCategory);
        List<LibraryItem> before = catalog.snapshot();
        List<LibraryItem> after = before.stream()
                .map(item -> removeCategory(item, name))
                .toList();
        replaceItemsAndConfiguration(
                before,
                after,
                new LibraryConfigurationSnapshot(preferences, categories));
    }

    @Override
    public synchronized void updateCategory(LibraryCategory category) {
        Objects.requireNonNull(category, "category must not be null");
        replaceCategory(category.name(), category);
    }

    @Override
    public synchronized void setCategoryUpdatePolicy(
            String name,
            LibraryCategoryUpdatePolicy policy) {
        Objects.requireNonNull(policy, "policy must not be null");
        LibraryConfigurationSnapshot current = normalizedConfiguration();
        LibraryCategory category = requireCategory(current.categories(), name);
        updateCategory(new LibraryCategory(
                name,
                category.displayMode(),
                category.density(),
                category.sort(),
                policy));
    }

    @Override
    public synchronized void setFavorite(Set<LibraryItemId> ids, boolean favorite) {
        replaceSelected(ids, item -> item.withFavorite(favorite));
    }

    @Override
    public synchronized void addToCategory(Set<LibraryItemId> ids, String category) {
        requireCategory(normalizedConfiguration().categories(), category);
        replaceSelected(ids, item -> {
            Set<String> categories = new LinkedHashSet<>(item.categories());
            categories.add(category);
            return item.withCategories(categories);
        });
    }

    @Override
    public synchronized void removeFromCategory(Set<LibraryItemId> ids, String category) {
        requireCategory(normalizedConfiguration().categories(), category);
        replaceSelected(ids, item -> removeCategory(item, category));
    }

    @Override
    public synchronized void deleteTitles(Set<LibraryItemId> ids) {
        Set<LibraryItemId> selected = validatedSelection(ids);
        catalog.replaceAll(catalog.snapshot().stream()
                .filter(item -> !selected.contains(item.id()))
                .toList());
    }

    @Override
    public synchronized void editTitle(
            LibraryItemId id,
            String title,
            LibraryTitleMetadata metadata) {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(metadata, "metadata must not be null");
        LibraryItem item = catalog.find(id)
                .orElseThrow(() -> new IllegalArgumentException("Unknown library title: " + id));
        catalog.save(item.withTitleAndMetadata(title, metadata));
    }

    @Override
    public synchronized List<LibraryCard> relatedTitles(LibraryItemId id) {
        Objects.requireNonNull(id, "id must not be null");
        LibraryItem selected = catalog.find(id)
                .orElseThrow(() -> new IllegalArgumentException("Unknown library title: " + id));
        return catalog.snapshot().stream()
                .filter(item -> !item.id().equals(id))
                .filter(item -> related(selected, item))
                .map(DefaultLibraryPresentation::card)
                .sorted(cardOrder(LibrarySort.TITLE_ASCENDING))
                .limit(20)
                .toList();
    }

    private void replaceSelected(
            Set<LibraryItemId> ids,
            UnaryOperator<LibraryItem> operation) {
        Objects.requireNonNull(operation, "operation must not be null");
        Set<LibraryItemId> selected = validatedSelection(ids);
        catalog.replaceAll(catalog.snapshot().stream()
                .map(item -> selected.contains(item.id()) ? operation.apply(item) : item)
                .toList());
    }

    private Set<LibraryItemId> validatedSelection(Set<LibraryItemId> ids) {
        Set<LibraryItemId> selected = Set.copyOf(
                Objects.requireNonNull(ids, "ids must not be null"));
        if (selected.isEmpty()) {
            throw new IllegalArgumentException("ids must not be empty");
        }
        Set<LibraryItemId> available = catalog.snapshot().stream()
                .map(LibraryItem::id)
                .collect(Collectors.toUnmodifiableSet());
        if (!available.containsAll(selected)) {
            throw new IllegalArgumentException("ids contain an unknown library title");
        }
        return selected;
    }

    private void updateActiveDisplay(
            Optional<LibraryDisplayMode> mode,
            Optional<LibraryDisplayDensity> density,
            Optional<LibrarySort> sort) {
        Objects.requireNonNull(mode, "mode must not be null");
        Objects.requireNonNull(density, "density must not be null");
        Objects.requireNonNull(sort, "sort must not be null");
        LibraryConfigurationSnapshot current = normalizedConfiguration();
        Optional<String> selected = current.displayPreferences().defaultCategory();
        if (selected.isPresent()) {
            LibraryCategory category = requireCategory(
                    current.categories(),
                    selected.orElseThrow());
            updateCategory(new LibraryCategory(
                    category.name(),
                    mode.orElse(category.displayMode()),
                    density.orElse(category.density()),
                    sort.orElse(category.sort()),
                    category.updatePolicy()));
            return;
        }
        LibraryDisplayPreferences preferences = current.displayPreferences();
        saveConfiguration(new LibraryConfigurationSnapshot(
                new LibraryDisplayPreferences(
                        mode.orElse(preferences.mode()),
                        density.orElse(preferences.density()),
                        sort.orElse(preferences.sort()),
                        preferences.defaultCategory()),
                current.categories()));
    }

    private LibraryConfigurationSnapshot normalizedConfiguration() {
        LibraryConfigurationSnapshot current = configuration.snapshot();
        List<LibraryCategory> effective = effectiveCategories(catalog.snapshot(), current);
        if (effective.equals(current.categories())) {
            return current;
        }
        LibraryConfigurationSnapshot normalized = new LibraryConfigurationSnapshot(
                current.displayPreferences(),
                effective);
        saveConfiguration(normalized);
        return normalized;
    }

    private void replaceItemsAndConfiguration(
            List<LibraryItem> before,
            List<LibraryItem> after,
            LibraryConfigurationSnapshot replacement) {
        catalog.replaceAll(after);
        try {
            saveConfiguration(replacement);
        } catch (RuntimeException failure) {
            try {
                catalog.replaceAll(before);
            } catch (RuntimeException rollbackFailure) {
                failure.addSuppressed(rollbackFailure);
            }
            throw failure;
        }
    }

    private void saveConfiguration(LibraryConfigurationSnapshot replacement) {
        configuration.save(replacement);
    }

    private static LibraryDisplayPreferences activePreferences(
            LibraryDisplayPreferences preferences,
            List<LibraryCategory> categories) {
        if (preferences.defaultCategory().isEmpty()) {
            return preferences;
        }
        String selected = preferences.defaultCategory().orElseThrow();
        return categories.stream()
                .filter(category -> category.name().equals(selected))
                .findFirst()
                .map(category -> new LibraryDisplayPreferences(
                        category.displayMode(),
                        category.density(),
                        category.sort(),
                        Optional.of(selected)))
                .orElse(preferences);
    }

    private static List<LibraryCategory> effectiveCategories(
            Collection<LibraryItem> items,
            LibraryConfigurationSnapshot configuration) {
        Map<String, LibraryCategory> categories = new LinkedHashMap<>();
        configuration.categories().forEach(category -> categories.put(category.name(), category));
        items.stream()
                .flatMap(item -> item.categories().stream())
                .distinct()
                .sorted(TEXT_ORDER)
                .forEach(name -> categories.putIfAbsent(
                        name,
                        LibraryCategory.defaults(name, configuration.displayPreferences())));
        return List.copyOf(categories.values());
    }

    private static LibraryCategory requireCategory(
            List<LibraryCategory> categories,
            String name) {
        Objects.requireNonNull(name, "name must not be null");
        return categories.stream()
                .filter(category -> category.name().equals(name))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unknown library category: " + name));
    }

    private static List<LibraryCategory> replaceCategory(
            List<LibraryCategory> categories,
            String name,
            LibraryCategory replacement) {
        return categories.stream()
                .map(category -> category.name().equals(name) ? replacement : category)
                .toList();
    }

    private static LibraryDisplayPreferences withDefaultCategory(
            LibraryDisplayPreferences preferences,
            Optional<String> defaultCategory) {
        return new LibraryDisplayPreferences(
                preferences.mode(),
                preferences.density(),
                preferences.sort(),
                defaultCategory);
    }

    private static LibraryItem renameCategory(
            LibraryItem item,
            String currentName,
            String nextName) {
        if (!item.categories().contains(currentName)) {
            return item;
        }
        Set<String> categories = new LinkedHashSet<>(item.categories());
        categories.remove(currentName);
        categories.add(nextName);
        return item.withCategories(categories);
    }

    private static LibraryItem removeCategory(LibraryItem item, String name) {
        if (!item.categories().contains(name)) {
            return item;
        }
        Set<String> categories = new LinkedHashSet<>(item.categories());
        categories.remove(name);
        return item.withCategories(categories);
    }

    private static Comparator<LibraryCard> cardOrder(LibrarySort sort) {
        Comparator<LibraryCard> title = Comparator
                .comparing(LibraryCard::title, TEXT_ORDER)
                .thenComparing(LibraryCard::id);
        Comparator<LibraryCard> added = Comparator
                .comparing(LibraryCard::addedAt)
                .thenComparing(LibraryCard::id);
        Comparator<LibraryCard> selected = switch (sort) {
            case TITLE_ASCENDING -> title;
            case TITLE_DESCENDING -> title.reversed();
            case ADDED_NEWEST -> added.reversed();
            case ADDED_OLDEST -> added;
        };
        return Comparator.comparing(LibraryCard::favorite)
                .reversed()
                .thenComparing(selected);
    }

    private static LibraryCard card(LibraryItem item) {
        return new LibraryCard(
                item.id(),
                item.title(),
                item.kind(),
                item.addedAt(),
                item.categories().stream().sorted(TEXT_ORDER).toList(),
                item.favorite(),
                item.progress(),
                item.metadata().artwork());
    }

    private static LibraryDetails details(LibraryItem item) {
        LibraryTitleMetadata metadata = item.metadata();
        return new LibraryDetails(
                item.id(),
                item.title(),
                item.kind(),
                item.addedAt(),
                item.categories().stream().sorted(TEXT_ORDER).toList(),
                item.favorite(),
                item.progress(),
                metadata.description(),
                metadata.authors(),
                metadata.artists(),
                metadata.publicationStatus(),
                metadata.artwork(),
                metadata.genres(),
                item.origin(),
                item.history().size());
    }

    private static boolean related(LibraryItem selected, LibraryItem candidate) {
        boolean sameSource = selected.origin().isPresent()
                && candidate.origin().isPresent()
                && selected.origin().orElseThrow().sourceId()
                .equals(candidate.origin().orElseThrow().sourceId());
        boolean sharedCategory = !Collections.disjoint(
                selected.categories(),
                candidate.categories());
        boolean sharedGenre = !Collections.disjoint(
                selected.metadata().genres(),
                candidate.metadata().genres());
        return selected.kind() == candidate.kind()
                && (sameSource || sharedCategory || sharedGenre);
    }

    private static LibraryHistoryRow historyRow(
            LibraryItem item,
            LibraryHistoryEntry entry) {
        return new LibraryHistoryRow(
                item.id(),
                item.title(),
                item.kind(),
                entry.contentId(),
                entry.openedAt(),
                entry.position());
    }
}
