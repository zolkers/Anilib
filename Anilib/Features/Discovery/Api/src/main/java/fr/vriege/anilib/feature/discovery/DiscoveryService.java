package fr.vriege.anilib.feature.discovery;

import fr.vriege.anilib.feature.library.LibraryItemId;
import fr.vriege.anilib.feature.source.SourceCatalogueItem;
import fr.vriege.anilib.feature.source.SourceContentKind;
import fr.vriege.anilib.feature.source.SourceDescriptor;
import fr.vriege.anilib.feature.source.InstalledSourceExtension;
import fr.vriege.anilib.feature.source.SourceFilterDefinition;
import fr.vriege.anilib.feature.source.SourceFilterValue;
import fr.vriege.anilib.feature.source.SourceId;
import fr.vriege.anilib.feature.source.SourceListing;
import fr.vriege.anilib.feature.source.SourcePage;

import java.util.List;
import java.util.Map;

/** Cross-source discovery, preferences, library admission, and migration boundary. */
public interface DiscoveryService {
    List<SourceDescriptor> sources(SourceContentKind contentKind);

    List<InstalledSourceExtension> extensions(SourceContentKind contentKind);

    boolean supportsLatest(SourceId sourceId);

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
