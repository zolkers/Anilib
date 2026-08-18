package fr.vriege.anilib.feature.discovery.ui;

import fr.vriege.anilib.feature.discovery.SourcePreferenceSnapshot;
import fr.vriege.anilib.feature.library.LibraryItemId;
import fr.vriege.anilib.feature.source.SourceCatalogueItem;
import fr.vriege.anilib.feature.source.SourceCatalogueItemId;
import fr.vriege.anilib.feature.source.SourceContentKind;
import fr.vriege.anilib.feature.source.InstalledSourceExtension;
import fr.vriege.anilib.feature.source.SourceFilterDefinition;
import fr.vriege.anilib.feature.source.SourceFilterValue;
import fr.vriege.anilib.feature.source.SourceId;
import fr.vriege.anilib.feature.source.SourceListing;
import fr.vriege.anilib.feature.source.SourcePage;
import fr.vriege.anilib.feature.source.SourceWebPage;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Platform-neutral snapshots and actions for the shared Browse experience. */
public interface DiscoveryPresentation {
    List<DiscoverySourceSection> sourceSections(SourceContentKind contentKind);

    List<InstalledSourceExtension> extensions(SourceContentKind contentKind);

    boolean supportsLatest(SourceId sourceId);

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

    LibraryItemId addToLibrary(SourceCatalogueItem item);

    List<SourceCatalogueItem> migrationCandidates(
            LibraryItemId libraryItemId,
            SourceId targetSourceId,
            int limit);

    void migrate(LibraryItemId libraryItemId, SourceCatalogueItem target);
}
