package fr.vriege.anilib.feature.discovery.ui;

import fr.vriege.anilib.feature.discovery.SourcePreferenceSnapshot;
import fr.vriege.anilib.feature.discovery.DiscoveryCatalogueDisplayMode;
import fr.vriege.anilib.feature.discovery.MigrationOptions;
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

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public interface DiscoveryPresentation {
    List<DiscoverySourceSection> sourceSections(SourceContentKind contentKind);

    Optional<SourceDescriptor> source(SourceId sourceId);

    List<String> availableSourceLanguages(SourceContentKind contentKind);

    Set<String> enabledSourceLanguages(SourceContentKind contentKind);

    Set<SourceId> pinnedSources();

    boolean sourceEnabled(SourceId sourceId);

    void setSourceLanguageEnabled(SourceContentKind contentKind, String languageTag, boolean enabled);

    void setSourcePinned(SourceId sourceId, boolean pinned);

    void setSourceEnabled(SourceId sourceId, boolean enabled);

    DiscoveryCatalogueDisplayMode catalogueDisplayMode(SourceId sourceId);

    void setCatalogueDisplayMode(SourceId sourceId, DiscoveryCatalogueDisplayMode displayMode);

    List<InstalledSourceExtension> extensions(SourceContentKind contentKind);

    boolean supportsLatest(SourceId sourceId);

    boolean supportsRefresh(SourceId sourceId);

    void refresh(SourceId sourceId);

    Optional<SourceWebPage> sourceWebPage(SourceId sourceId);

    Optional<SourceWebPage> titleWebPage(SourceCatalogueItemId itemId);

    SourcePage browse(
            SourceId sourceId,
            SourceListing listing,
            int page,
            int pageSize,
            List<SourceFilterValue> filters);

    SourcePage search(
            SourceId sourceId,
            String query,
            int page,
            int pageSize,
            List<SourceFilterValue> filters);

    Map<SourceId, SourcePage> globalSearch(SourceContentKind contentKind, String query, int pageSize);

    List<SourceFilterDefinition> filters(SourceId sourceId);

    List<SourcePreferenceSnapshot> preferences(SourceId sourceId);

    void setPreference(SourceId sourceId, String preferenceId, String value);

    SourceTitleDetails titleDetails(SourceCatalogueItem item);

    List<SourceContentUnit> contentUnits(SourceCatalogueItemId itemId);

    List<SourceEpisode> episodes(SourceCatalogueItemId itemId);

    Optional<LibraryItemId> libraryItem(SourceCatalogueItemId itemId);

    Optional<LibraryItemId> indexedItem(SourceCatalogueItemId itemId);

    LibraryItemId index(SourceCatalogueItem item);

    LibraryItemId addToLibrary(SourceCatalogueItem item);

    boolean removeFromLibrary(SourceCatalogueItemId itemId);

    List<SourceCatalogueItem> migrationCandidates(
            LibraryItemId libraryItemId,
            SourceId targetSourceId,
            int limit);

    default List<SourceCatalogueItem> migrationCandidates(
            LibraryItemId libraryItemId,
            SourceId targetSourceId,
            MigrationOptions options,
            int limit) {
        return migrationCandidates(libraryItemId, targetSourceId, limit);
    }

    void migrate(LibraryItemId libraryItemId, SourceCatalogueItem target);

    default void migrate(
            LibraryItemId libraryItemId,
            SourceCatalogueItem target,
            MigrationOptions options) {
        migrate(libraryItemId, target);
    }
}
