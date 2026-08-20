package fr.vriege.anilib.tooling.archtests;

import fr.vriege.anilib.configuration.standard.StandardAnilib;
import fr.vriege.anilib.feature.discovery.DiscoveryCapabilities;
import fr.vriege.anilib.feature.discovery.DiscoveryCatalogueDisplayMode;
import fr.vriege.anilib.feature.discovery.DiscoveryService;
import fr.vriege.anilib.feature.discovery.MigrationOptions;
import fr.vriege.anilib.feature.discovery.ui.DiscoveryPresentation;
import fr.vriege.anilib.feature.discovery.ui.DiscoveryUiCapabilities;
import fr.vriege.anilib.feature.library.LibraryCapabilities;
import fr.vriege.anilib.feature.library.LibraryCatalog;
import fr.vriege.anilib.feature.library.LibraryHistoryEntry;
import fr.vriege.anilib.feature.library.LibraryItem;
import fr.vriege.anilib.feature.library.LibraryItemId;
import fr.vriege.anilib.feature.library.LibraryProgress;
import fr.vriege.anilib.feature.library.MediaKind;
import fr.vriege.anilib.feature.source.CatalogueSource;
import fr.vriege.anilib.feature.source.SourceBrowseRequest;
import fr.vriege.anilib.feature.source.SourceCatalogueItem;
import fr.vriege.anilib.feature.source.SourceCatalogueItemId;
import fr.vriege.anilib.feature.source.SourceContentKind;
import fr.vriege.anilib.feature.source.SourceDescriptor;
import fr.vriege.anilib.feature.source.SourceExtensionPlugin;
import fr.vriege.anilib.feature.source.SourceFilterValue;
import fr.vriege.anilib.feature.source.SourceId;
import fr.vriege.anilib.feature.source.SourceListing;
import fr.vriege.anilib.feature.source.SourcePage;
import fr.vriege.anilib.feature.source.SourceSdk;
import fr.vriege.anilib.feature.source.SourceSearchRequest;
import fr.vriege.anilib.feature.source.SourceWebPage;
import fr.vriege.anilib.feature.source.WebSource;
import fr.vriege.anilib.foundation.component.ComponentDescriptor;
import fr.vriege.anilib.kernel.StartedAnilib;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

final class DiscoveryTest {
    private static final SourceId LOCAL_SOURCE = SourceId.of("anilib.local");
    private static final SourceId REMOTE_SOURCE = SourceId.of("test.remote");
    private static final SourceCatalogueItem REMOTE_ITEM = new SourceCatalogueItem(
            new SourceCatalogueItemId(REMOTE_SOURCE, "alpha-hero-reborn"),
            "Alpha Hero Reborn",
            "A remote migration target.",
            Optional.empty(),
            SourceContentKind.MANGA);
    private static final SourceCatalogueItem REMOTE_ANIME_ITEM = new SourceCatalogueItem(
            new SourceCatalogueItemId(REMOTE_SOURCE, "alpha-hero"),
            "Alpha Hero",
            "A seasonal anime migration target.",
            Optional.empty(),
            SourceContentKind.ANIME);

    private DiscoveryTest() {
    }

    static int run() {
        Counter counter = new Counter();
        Path directory;
        try {
            directory = Files.createTempDirectory("anilib-discovery");
            Files.createDirectories(directory.resolve("local-content").resolve("Alpha Hero"));
            Files.createDirectories(directory.resolve("local-content").resolve("Beta Tale"));
        } catch (IOException exception) {
            throw new AssertionError("Unable to create discovery test data", exception);
        }
        try {
            verifiesDiscoveryProduct(counter, directory);
            verifiesPreferenceRestart(counter, directory);
            verifiesBrowsePreferenceRestart(counter, directory);
        } finally {
            deleteDirectory(directory);
        }
        return counter.value;
    }

    private static void verifiesDiscoveryProduct(Counter counter, Path directory) {
        SourceExtensionPlugin remotePlugin = new SourceExtensionPlugin(
                ComponentDescriptor.of("test.remote-source", "Remote source", "1.0.0"),
                new TestCatalogueSource());
        try (StartedAnilib product = StandardAnilib.start(directory, List.of(remotePlugin))) {
            DiscoveryService discovery = product.capability(DiscoveryCapabilities.SERVICE);
            DiscoveryPresentation presentation = product.capability(DiscoveryUiCapabilities.PRESENTATION);
            LibraryCatalog library = product.capability(LibraryCapabilities.CATALOG);

            counter.check(discovery.sources(SourceContentKind.MANGA).stream()
                            .map(SourceDescriptor::id)
                            .toList()
                            .equals(List.of(REMOTE_SOURCE, LOCAL_SOURCE)),
                    "catalogue sources must be grouped in deterministic language order");
            counter.check(presentation.sourceSections(SourceContentKind.MANGA).size() == 2,
                    "the shared presentation must expose language sections");
            counter.check(presentation.enabledSourceLanguages(SourceContentKind.MANGA)
                            .equals(Set.of("en", "und")),
                    "default source languages must retain local content and English without enabling every locale");
            counter.check(discovery.source(REMOTE_SOURCE).orElseThrow().languageTag().equals("en")
                            && presentation.source(REMOTE_SOURCE).orElseThrow()
                                    .displayName().equals("Remote catalogue"),
                    "source identity and language must remain available outside browse filters");
            counter.check(presentation.extensions(SourceContentKind.MANGA).size() == 1
                            && presentation.extensions(SourceContentKind.MANGA)
                                    .getFirst().source().id().equals(REMOTE_SOURCE),
                    "the shared presentation must expose selected extension Bundles but not built-ins");
            counter.check(discovery.supportsLatest(LOCAL_SOURCE),
                    "local catalogue must expose its latest listing");
            SourceWebPage sourcePage = presentation.sourceWebPage(REMOTE_SOURCE).orElseThrow();
            counter.check(sourcePage.location().equals(URI.create("https://catalogue.example.test/"))
                            && sourcePage.headers().equals(Map.of("Accept-Language", "en-US"))
                            && sourcePage.userAgent().orElseThrow().equals("Anilib-Test/1.0")
                            && sourcePage.completionCookies().equals(Set.of("cf_clearance")),
                    "the shared presentation must expose optional source browser entry points");
            SourceWebPage titlePage = presentation.titleWebPage(REMOTE_ITEM.id()).orElseThrow();
            counter.check(titlePage.location().equals(
                            URI.create("https://catalogue.example.test/title/alpha-hero-reborn"))
                            && titlePage.userAgent().equals(sourcePage.userAgent())
                            && titlePage.completionCookies().equals(sourcePage.completionCookies()),
                    "the shared presentation must expose optional title browser entry points");
            counter.check(presentation.sourceWebPage(LOCAL_SOURCE).isEmpty(),
                    "sources without a web contract must not expose a browser action");

            SourcePage firstPage = discovery.browse(
                    LOCAL_SOURCE,
                    SourceListing.POPULAR,
                    1,
                    1,
                    List.of());
            counter.check(firstPage.items().getFirst().title().equals("Alpha Hero") && firstPage.hasNextPage(),
                    "browse must paginate deterministic source results");
            counter.check(discovery.search(LOCAL_SOURCE, "beta", 1, 20, List.of())
                            .items().getFirst().title().equals("Beta Tale"),
                    "source search must match titles without platform behavior");
            counter.check(discovery.browse(
                            LOCAL_SOURCE,
                            SourceListing.POPULAR,
                            1,
                            20,
                            List.of(new SourceFilterValue("sort", "Title descending")))
                            .items().getFirst().title().equals("Beta Tale"),
                    "source filters must reach the catalogue implementation");
            counter.check(discovery.globalSearch(SourceContentKind.MANGA, "alpha", 20)
                            .keySet().equals(Set.of(LOCAL_SOURCE, REMOTE_SOURCE)),
                    "global search must aggregate every compatible source");
            counter.expectIllegalArgument(() -> discovery.browse(
                            LOCAL_SOURCE,
                            SourceListing.POPULAR,
                            1,
                            20,
                            List.of(new SourceFilterValue("missing", "value"))),
                    "unknown filters must be rejected before reaching a source");
            counter.expectIllegalArgument(() -> discovery.setPreference(
                            LOCAL_SOURCE,
                            "include-manga",
                            "sometimes"),
                    "invalid source preference values must be rejected");

            SourceCatalogueItem localItem = discovery.search(LOCAL_SOURCE, "alpha", 1, 20, List.of())
                    .items().getFirst();
            LibraryItemId libraryItemId = discovery.addToLibrary(localItem);
            counter.check(discovery.addToLibrary(localItem).equals(libraryItemId)
                            && library.snapshot().size() == 1,
                    "adding the same source identity twice must not duplicate the library title");
            LibraryItem enriched = library.find(libraryItemId).orElseThrow()
                    .withCategories(Set.of("Reading"))
                    .withFavorite(true)
                    .withProgress(new LibraryProgress("chapter-3", 4L, 10L, Instant.parse("2026-08-17T10:00:00Z")))
                    .recordHistory(new LibraryHistoryEntry(
                            "chapter-2",
                            Instant.parse("2026-08-16T10:00:00Z"),
                            10L));
            library.save(enriched);

            List<SourceCatalogueItem> candidates = discovery.migrationCandidates(
                    libraryItemId,
                    REMOTE_SOURCE,
                    20);
            counter.check(candidates.equals(List.of(REMOTE_ITEM)),
                    "migration must search the explicitly selected target source");
            discovery.migrate(libraryItemId, candidates.getFirst(), new MigrationOptions(true, false));
            counter.check(library.find(libraryItemId).orElseThrow().title().equals("Alpha Hero"),
                    "migration options must preserve the user-owned title when requested");
            discovery.migrate(libraryItemId, candidates.getFirst());
            LibraryItem migrated = library.find(libraryItemId).orElseThrow();
            counter.check(migrated.id().equals(libraryItemId)
                            && migrated.title().equals(REMOTE_ITEM.title())
                            && migrated.origin().orElseThrow().sourceId().equals(REMOTE_SOURCE.toString()),
                    "migration must retain library identity while changing source identity");
            counter.check(migrated.favorite()
                            && migrated.categories().equals(Set.of("Reading"))
                            && migrated.progress().equals(enriched.progress())
                            && migrated.history().equals(enriched.history()),
                    "migration must preserve user-owned library state");

            LibraryItem seasonalAnime = LibraryItem.create("Alpha Hero Season 2", MediaKind.ANIME);
            library.save(seasonalAnime);
            counter.check(discovery.migrationCandidates(
                            seasonalAnime.id(),
                            REMOTE_SOURCE,
                            new MigrationOptions(false, true),
                            20).equals(List.of(REMOTE_ANIME_ITEM)),
                    "seasonal anime migration must search a normalized franchise title");

            discovery.setPreference(LOCAL_SOURCE, "include-manga", "false");
            counter.check(discovery.browse(LOCAL_SOURCE, SourceListing.POPULAR, 1, 20, List.of()).items().isEmpty(),
                    "source preferences must affect subsequent browse requests immediately");
        }
    }

    private static void verifiesPreferenceRestart(Counter counter, Path directory) {
        try (StartedAnilib product = StandardAnilib.start(directory)) {
            DiscoveryService discovery = product.capability(DiscoveryCapabilities.SERVICE);
            String includeManga = discovery.preferences(LOCAL_SOURCE).stream()
                    .filter(preference -> preference.definition().id().equals("include-manga"))
                    .findFirst()
                    .orElseThrow()
                    .value();
            counter.check(includeManga.equals("false"),
                    "source preferences must survive a complete product restart");
        }
    }

    private static void verifiesBrowsePreferenceRestart(Counter counter, Path directory) {
        SourceExtensionPlugin remotePlugin = new SourceExtensionPlugin(
                ComponentDescriptor.of("test.remote-source", "Remote source", "1.0.0"),
                new TestCatalogueSource());
        try (StartedAnilib product = StandardAnilib.start(directory, List.of(remotePlugin))) {
            DiscoveryPresentation presentation = product.capability(DiscoveryUiCapabilities.PRESENTATION);
            presentation.setSourcePinned(REMOTE_SOURCE, true);
            presentation.setSourceEnabled(REMOTE_SOURCE, false);
            presentation.setSourceLanguageEnabled(SourceContentKind.MANGA, "en", false);
            presentation.setCatalogueDisplayMode(REMOTE_SOURCE, DiscoveryCatalogueDisplayMode.LIST);
            counter.check(presentation.pinnedSources().equals(Set.of(REMOTE_SOURCE)),
                    "pinned sources must be reflected by the shared presentation immediately");
            counter.check(!presentation.sourceEnabled(REMOTE_SOURCE),
                    "an individual extension source must be disabled without removing its package");
            counter.check(!presentation.globalSearch(SourceContentKind.MANGA, "alpha", 20)
                            .containsKey(REMOTE_SOURCE),
                    "disabled extension sources must be excluded from global search");
            counter.check(presentation.sourceSections(SourceContentKind.MANGA).stream()
                            .flatMap(section -> section.sources().stream())
                            .noneMatch(source -> source.id().equals(REMOTE_SOURCE)),
                    "disabled source languages must be removed from Browse immediately");
        }

        SourceExtensionPlugin reopenedPlugin = new SourceExtensionPlugin(
                ComponentDescriptor.of("test.remote-source", "Remote source", "1.0.0"),
                new TestCatalogueSource());
        try (StartedAnilib product = StandardAnilib.start(directory, List.of(reopenedPlugin))) {
            DiscoveryPresentation presentation = product.capability(DiscoveryUiCapabilities.PRESENTATION);
            counter.check(presentation.pinnedSources().equals(Set.of(REMOTE_SOURCE)),
                    "pinned sources must survive Android and desktop restart");
            counter.check(!presentation.enabledSourceLanguages(SourceContentKind.MANGA).contains("en"),
                    "source language choices must survive Android and desktop restart");
            counter.check(!presentation.sourceEnabled(REMOTE_SOURCE),
                    "individual extension source choices must survive Android and desktop restart");
            counter.check(presentation.catalogueDisplayMode(REMOTE_SOURCE)
                            == DiscoveryCatalogueDisplayMode.LIST,
                    "catalogue display choices must survive Android and desktop restart");
        }
    }

    private static void deleteDirectory(Path directory) {
        try (Stream<Path> entries = Files.walk(directory)) {
            for (Path entry : entries.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(entry);
            }
        } catch (IOException exception) {
            throw new AssertionError("Unable to clean discovery test directory", exception);
        }
    }

    private static final class TestCatalogueSource implements CatalogueSource, WebSource {
        private static final SourceDescriptor DESCRIPTOR = new SourceDescriptor(
                REMOTE_SOURCE,
                "Remote catalogue",
                "1.0.0",
                "en",
                Set.of(SourceContentKind.ANIME, SourceContentKind.MANGA),
                SourceSdk.API_VERSION);

        @Override
        public SourceDescriptor descriptor() {
            return DESCRIPTOR;
        }

        @Override
        public SourcePage popular(SourceBrowseRequest request) {
            return new SourcePage(List.of(REMOTE_ITEM, REMOTE_ANIME_ITEM), false);
        }

        @Override
        public boolean supportsLatest() {
            return true;
        }

        @Override
        public SourcePage latest(SourceBrowseRequest request) {
            return popular(request);
        }

        @Override
        public SourcePage search(SourceSearchRequest request) {
            String query = request.query().toLowerCase(Locale.ROOT);
            List<SourceCatalogueItem> matches = List.of(REMOTE_ITEM, REMOTE_ANIME_ITEM).stream()
                    .filter(item -> item.title().toLowerCase(Locale.ROOT).contains(query))
                    .toList();
            return new SourcePage(matches, false);
        }

        @Override
        public URI homePage() {
            return URI.create("https://catalogue.example.test/");
        }

        @Override
        public Optional<URI> titlePage(SourceCatalogueItemId itemId) {
            return Optional.of(URI.create("https://catalogue.example.test/title/" + itemId.value()));
        }

        @Override
        public Map<String, String> browserHeaders(URI location) {
            return Map.of("Accept-Language", "en-US");
        }

        @Override
        public Optional<String> browserUserAgent(URI location) {
            return Optional.of("Anilib-Test/1.0");
        }

        @Override
        public Set<String> browserCompletionCookies(URI location) {
            return Set.of("cf_clearance");
        }
    }

    private static final class Counter {
        private int value;

        private void check(boolean condition, String message) {
            value++;
            if (!condition) {
                throw new AssertionError(message);
            }
        }

        private void expectIllegalArgument(Runnable action, String message) {
            try {
                action.run();
                throw new AssertionError(message);
            } catch (IllegalArgumentException expected) {
                value++;
            }
        }
    }
}
