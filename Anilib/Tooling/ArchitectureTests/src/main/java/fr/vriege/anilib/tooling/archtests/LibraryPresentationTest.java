package fr.vriege.anilib.tooling.archtests;

import fr.vriege.anilib.feature.library.LibraryHistoryEntry;
import fr.vriege.anilib.feature.library.LibraryCategoryUpdatePolicy;
import fr.vriege.anilib.feature.library.LibraryDisplayDensity;
import fr.vriege.anilib.feature.library.LibraryDisplayMode;
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

final class LibraryPresentationTest {
    private LibraryPresentationTest() {
    }

    static int run() {
        Counter counter = new Counter();
        InMemoryLibraryCatalog catalog = populatedCatalog();
        LibraryPresentation presentation = new DefaultLibraryPresentation(
                catalog,
                new InMemoryLibraryConfiguration());

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

        presentation.setDefaultCategory(java.util.Optional.of("Anime"));
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

        presentation.createCategory("Archive");
        presentation.moveCategory("Archive", 0);
        presentation.renameCategory("Anime", "Animation");
        counter.check(catalog.find(new LibraryItemId("zulu")).orElseThrow()
                        .categories().contains("Animation"),
                "renaming a category must update assigned titles");
        counter.check(presentation.library().displayPreferences().defaultCategory()
                        .orElseThrow().equals("Animation"),
                "renaming the landing category must preserve the selection");
        presentation.deleteCategory("Animation");
        counter.check(catalog.find(new LibraryItemId("zulu")).orElseThrow()
                        .categories().equals(Set.of("Seasonal")),
                "deleting a category must remove title assignments");
        counter.check(presentation.library().displayPreferences().defaultCategory().isEmpty(),
                "deleting the landing category must restore the all-titles landing");

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
        counter.check(presentation.details(new LibraryItemId("missing")).isEmpty(),
                "missing details must remain explicit");

        counter.check(
                presentation.history().entries().stream().map(row -> row.contentId()).toList()
                        .equals(List.of("episode-2", "chapter-1", "episode-1")),
                "global history must be reverse chronological across titles");
        counter.check(presentation.history().entries().getFirst().title().equals("Zulu"),
                "history rows must retain their owning title");

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
                        PublicationStatus.ONGOING));
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
                MediaKind.NOVEL,
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
        return new LibraryNavigationState(page, java.util.Optional.empty());
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
