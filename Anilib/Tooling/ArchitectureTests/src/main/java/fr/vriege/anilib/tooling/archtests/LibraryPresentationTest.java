package fr.vriege.anilib.tooling.archtests;

import fr.vriege.anilib.feature.library.LibraryHistoryEntry;
import fr.vriege.anilib.feature.library.LibraryCategoryUpdatePolicy;
import fr.vriege.anilib.feature.library.LibraryCategory;
import fr.vriege.anilib.feature.library.LibraryCategoryScope;
import fr.vriege.anilib.feature.library.LibraryDisplayDensity;
import fr.vriege.anilib.feature.library.LibraryDisplayMode;
import fr.vriege.anilib.feature.library.LibrarySort;
import fr.vriege.anilib.feature.library.LibraryItem;
import fr.vriege.anilib.feature.library.LibraryItemId;
import fr.vriege.anilib.feature.library.LibraryProgress;
import fr.vriege.anilib.feature.library.LibraryTitleMetadata;
import fr.vriege.anilib.feature.library.MediaKind;
import fr.vriege.anilib.feature.library.PublicationStatus;
import fr.vriege.anilib.feature.library.core.InMemoryLibraryCatalog;
import fr.vriege.anilib.feature.library.core.InMemoryLibraryConfiguration;
import fr.vriege.anilib.feature.library.ui.DefaultLibraryPresentation;
import fr.vriege.anilib.feature.library.ui.LibraryDetails;
import fr.vriege.anilib.feature.library.ui.LibraryNavigationState;
import fr.vriege.anilib.feature.library.ui.LibraryNavigator;
import fr.vriege.anilib.feature.library.ui.LibraryOverview;
import fr.vriege.anilib.feature.library.ui.LibraryPage;
import fr.vriege.anilib.feature.library.ui.LibraryPresentation;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.net.URI;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

final class LibraryPresentationTest {
    private LibraryPresentationTest() {
    }

    static int run() {
        Counter counter = new Counter();
        InMemoryLibraryCatalog catalog = populatedCatalog();
        LibraryPresentation presentation = new DefaultLibraryPresentation(
                catalog,
                new InMemoryLibraryConfiguration());
        AtomicInteger revisions = new AtomicInteger();
        AutoCloseable observation = presentation.observe(revisions::incrementAndGet);
        catalog.save(catalog.find(new LibraryItemId("alpha")).orElseThrow());
        try {
            observation.close();
        } catch (Exception exception) {
            throw new AssertionError("Unable to close Library presentation observation", exception);
        }
        counter.check(revisions.get() == 1,
                "Library presentation observers must receive catalog history and progress mutations");

        LibraryOverview overview = presentation.library();
        counter.check(
                overview.titles().stream().map(card -> card.title()).toList()
                        .equals(List.of("Zulu", "Alpha", "beta")),
                "favorite titles must precede case-insensitive title order");
        counter.check(overview.favoriteCount() == 1, "overview must count favorite titles");
        counter.check(overview.categories().equals(List.of("Anime", "Seasonal", "Watch later")),
                "overview categories must be unique and deterministically ordered");
        counter.check(overview.titles().getFirst().categories().equals(List.of("Anime", "Seasonal")),
                "cards must expose sorted categories");
        counter.check(overview.titles().getFirst().progress().orElseThrow().position() == 7,
                "cards must preserve typed progress");
        counter.check(overview.displayPreferences().mode() == LibraryDisplayMode.GRID,
                "library display mode must have a stable default");

        presentation.setDefaultCategory(Optional.of("Anime"));
        presentation.setDisplayMode(LibraryDisplayMode.LIST);
        presentation.setDisplayDensity(LibraryDisplayDensity.COMPACT);
        presentation.setCategoryUpdatePolicy("Anime", LibraryCategoryUpdatePolicy.EXCLUDE);
        overview = presentation.library();
        counter.check(overview.displayPreferences().mode() == LibraryDisplayMode.LIST
                        && overview.displayPreferences().density() == LibraryDisplayDensity.COMPACT,
                "the selected category must own its display preferences");
        counter.check(overview.categoryConfigurations().getFirst().updatePolicy()
                        == LibraryCategoryUpdatePolicy.EXCLUDE,
                "category update policy must be editable");
        counter.check(presentation.relatedTitles(new LibraryItemId("zulu")).stream()
                        .anyMatch(card -> card.id().equals(new LibraryItemId("beta"))),
                "related titles must include same-kind category matches");

        presentation.createCategory(new LibraryCategory(
                "Archive",
                LibraryCategoryScope.SHARED,
                LibraryDisplayMode.LIST,
                LibraryDisplayDensity.RELAXED,
                LibrarySort.ADDED_OLDEST,
                LibraryCategoryUpdatePolicy.INCLUDE));
        counter.check(presentation.library().categoryConfigurations().stream()
                        .anyMatch(category -> category.name().equals("Archive")
                                && category.displayMode() == LibraryDisplayMode.LIST
                                && category.density() == LibraryDisplayDensity.RELAXED
                                && category.sort() == LibrarySort.ADDED_OLDEST
                                && category.updatePolicy() == LibraryCategoryUpdatePolicy.INCLUDE),
                "category creation must retain every category preference");
        presentation.moveCategory("Archive", 0);
        presentation.replaceCategory("Anime", new LibraryCategory(
                "Animation",
                LibraryCategoryScope.ANIME,
                LibraryDisplayMode.GRID,
                LibraryDisplayDensity.COMFORTABLE,
                LibrarySort.TITLE_DESCENDING,
                LibraryCategoryUpdatePolicy.DEFAULT));
        counter.check(catalog.find(new LibraryItemId("zulu")).orElseThrow()
                        .categories().contains("Animation"),
                "renaming a category must update assigned titles");
        counter.check(presentation.library().displayPreferences().defaultCategory()
                        .orElseThrow().equals("Animation"),
                "renaming the landing category must preserve the selection");
        counter.check(presentation.library().categoryConfigurations().stream()
                        .anyMatch(category -> category.name().equals("Animation")
                                && category.sort() == LibrarySort.TITLE_DESCENDING),
                "category replacement must persist its display configuration");
        presentation.deleteCategory("Animation");
        counter.check(catalog.find(new LibraryItemId("zulu")).orElseThrow()
                        .categories().equals(Set.of("Seasonal")),
                "deleting a category must remove title assignments");
        counter.check(presentation.library().displayPreferences().defaultCategory().isEmpty(),
                "deleting the landing category must restore the all-titles landing");

        Set<LibraryItemId> bulkSelection = Set.of(
                new LibraryItemId("alpha"),
                new LibraryItemId("beta"));
        presentation.addToCategory(bulkSelection, "Archive");
        presentation.setFavorite(bulkSelection, true);
        counter.check(catalog.find(new LibraryItemId("alpha")).orElseThrow().favorite()
                        && catalog.find(new LibraryItemId("beta")).orElseThrow()
                        .categories().contains("Archive"),
                "bulk category and favourite actions must update every selected title");
        presentation.removeFromCategory(bulkSelection, "Archive");
        presentation.setCategoryTitles("Archive", Set.of(new LibraryItemId("alpha")));
        counter.check(catalog.find(new LibraryItemId("alpha")).orElseThrow()
                        .categories().contains("Archive")
                        && !catalog.find(new LibraryItemId("beta")).orElseThrow()
                        .categories().contains("Archive"),
                "category title management must replace the exact assignment set");
        presentation.setFavorite(Set.of(new LibraryItemId("alpha")), false);
        presentation.setTitleCategories(new LibraryItemId("alpha"), Set.of("Archive"));
        presentation.setTitleCategories(new LibraryItemId("zulu"), Set.of());
        counter.check(catalog.find(new LibraryItemId("alpha")).orElseThrow()
                        .categories().equals(Set.of("Archive"))
                        && !catalog.find(new LibraryItemId("alpha")).orElseThrow().favorite()
                        && catalog.find(new LibraryItemId("zulu")).orElseThrow()
                        .categories().isEmpty(),
                "title categories must replace assignments without changing favorite state");
        presentation.deleteTitles(Set.of(new LibraryItemId("beta")));
        counter.check(catalog.find(new LibraryItemId("beta")).isEmpty()
                        && catalog.find(new LibraryItemId("alpha")).orElseThrow()
                        .categories().contains("Archive"),
                "bulk category removal and deletion must be atomic catalog mutations");

        LibraryItemId zuluId = overview.titles().getFirst().id();
        LibraryDetails details = presentation.details(zuluId).orElseThrow();
        counter.check(details.description().equals("A complete presentation title."),
                "details must expose description metadata");
        counter.check(details.authors().equals(List.of("Author One")),
                "details must expose authors");
        counter.check(details.publicationStatus() == PublicationStatus.ONGOING,
                "details must expose publication status");
        counter.check(details.historyEntryCount() == 2,
                "details must expose title history count");
        counter.check(details.artwork().orElseThrow().getHost().equals("images.example")
                        && details.genres().equals(List.of("Action")),
                "details must expose artwork and genres");
        counter.check(presentation.details(new LibraryItemId("missing")).isEmpty(),
                "missing details must remain explicit");

        counter.check(
                presentation.history().entries().stream().map(row -> row.contentId()).toList()
                        .equals(List.of("episode-2", "chapter-1", "episode-1")),
                "global history must be reverse chronological across titles");
        counter.check(presentation.history().entries().getFirst().title().equals("Zulu"),
                "history rows must retain their owning title");
        counter.check(presentation.history().entries().getFirst().kind() == MediaKind.ANIME
                        && presentation.history().entries().get(1).kind() == MediaKind.MANGA,
                "history rows must distinguish anime and manga presentation");
        var removedHistory = presentation.history().entries().getFirst();
        presentation.removeHistoryEntry(
                removedHistory.libraryItemId(),
                removedHistory.contentId(),
                removedHistory.openedAt());
        counter.check(presentation.history().entries().stream()
                        .noneMatch(row -> row.libraryItemId().equals(removedHistory.libraryItemId())
                                && row.contentId().equals(removedHistory.contentId())
                                && row.openedAt().equals(removedHistory.openedAt())),
                "history removal must durably target the selected visit");
        LibraryTitleMetadata edited = new LibraryTitleMetadata(
                "Edited description",
                List.of("Editor"),
                List.of(),
                PublicationStatus.COMPLETED,
                Optional.empty(),
                List.of("Drama"));
        presentation.editTitle(zuluId, "Edited Zulu", edited);
        counter.check(presentation.details(zuluId).orElseThrow().title().equals("Edited Zulu")
                        && presentation.details(zuluId).orElseThrow().description()
                        .equals("Edited description"),
                "detail editing must durably replace title metadata");

        verifyNavigation(counter, zuluId);
        return counter.value;
    }

    private static InMemoryLibraryCatalog populatedCatalog() {
        InMemoryLibraryCatalog catalog = new InMemoryLibraryCatalog();
        LibraryItem zulu = new LibraryItem(
                new LibraryItemId("zulu"),
                "Zulu",
                MediaKind.ANIME,
                Instant.parse("2026-08-01T10:00:00Z"),
                Set.of("Seasonal", "Anime"))
                .withFavorite(true)
                .withProgress(new LibraryProgress(
                        "episode-2",
                        7,
                        12,
                        Instant.parse("2026-08-17T11:00:00Z")))
                .recordHistory(new LibraryHistoryEntry(
                        "episode-1",
                        Instant.parse("2026-08-15T10:00:00Z"),
                        12))
                .recordHistory(new LibraryHistoryEntry(
                        "episode-2",
                        Instant.parse("2026-08-17T10:00:00Z"),
                        7))
                .withMetadata(new LibraryTitleMetadata(
                        "A complete presentation title.",
                        List.of("Author One"),
                        List.of("Artist One"),
                        PublicationStatus.ONGOING,
                        Optional.of(URI.create(
                                "https://images.example/zulu.jpg")),
                        List.of("Action")));
        LibraryItem alpha = new LibraryItem(
                new LibraryItemId("alpha"),
                "Alpha",
                MediaKind.MANGA,
                Instant.parse("2026-08-02T10:00:00Z"),
                Set.of("Watch later"))
                .recordHistory(new LibraryHistoryEntry(
                        "chapter-1",
                        Instant.parse("2026-08-16T10:00:00Z"),
                        3));
        LibraryItem beta = new LibraryItem(
                new LibraryItemId("beta"),
                "beta",
                MediaKind.ANIME,
                Instant.parse("2026-08-03T10:00:00Z"),
                Set.of("Anime"));
        catalog.save(beta);
        catalog.save(zulu);
        catalog.save(alpha);
        return catalog;
    }

    private static void verifyNavigation(Counter counter, LibraryItemId titleId) {
        LibraryNavigator navigator = new LibraryNavigator();
        counter.check(navigator.state().equals(state(LibraryPage.LIBRARY)),
                "navigation must start on the library page");
        navigator.openHistory();
        navigator.openDetails(titleId);
        counter.check(navigator.state().page() == LibraryPage.DETAILS
                        && navigator.state().selectedTitle().orElseThrow().equals(titleId),
                "details navigation must retain the selected title");
        navigator.back();
        counter.check(navigator.state().equals(state(LibraryPage.HISTORY)),
                "details opened from history must return to history");
        navigator.openLibrary();
        navigator.openDetails(titleId);
        navigator.back();
        counter.check(navigator.state().equals(state(LibraryPage.LIBRARY)),
                "details opened from library must return to library");
    }

    private static LibraryNavigationState state(LibraryPage page) {
        return new LibraryNavigationState(page, Optional.empty());
    }

    private static final class Counter {
        private int value;

        private void check(boolean condition, String message) {
            value++;
            if (!condition) {
                throw new AssertionError(message);
            }
        }
    }
}
