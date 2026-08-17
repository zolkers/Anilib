package fr.vriege.anilib.feature.library.ui;

import fr.vriege.anilib.feature.library.LibraryCatalog;
import fr.vriege.anilib.feature.library.LibraryHistoryEntry;
import fr.vriege.anilib.feature.library.LibraryItem;
import fr.vriege.anilib.feature.library.LibraryItemId;
import fr.vriege.anilib.feature.library.LibraryTitleMetadata;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Deterministic presentation mapping shared by every platform renderer. */
public final class DefaultLibraryPresentation implements LibraryPresentation {
    private static final Comparator<String> TEXT_ORDER =
            String.CASE_INSENSITIVE_ORDER.thenComparing(Comparator.naturalOrder());
    private static final Comparator<LibraryCard> CARD_ORDER =
            Comparator.comparing(LibraryCard::favorite).reversed()
                    .thenComparing(LibraryCard::title, TEXT_ORDER)
                    .thenComparing(LibraryCard::id);
    private static final Comparator<LibraryHistoryRow> HISTORY_ORDER =
            Comparator.comparing(LibraryHistoryRow::openedAt).reversed()
                    .thenComparing(LibraryHistoryRow::title, TEXT_ORDER)
                    .thenComparing(LibraryHistoryRow::libraryItemId)
                    .thenComparing(LibraryHistoryRow::contentId);

    private final LibraryCatalog catalog;

    public DefaultLibraryPresentation(LibraryCatalog catalog) {
        this.catalog = Objects.requireNonNull(catalog, "catalog must not be null");
    }

    @Override
    public LibraryOverview library() {
        List<LibraryItem> items = catalog.snapshot();
        List<LibraryCard> cards = items.stream().map(DefaultLibraryPresentation::card).sorted(CARD_ORDER).toList();
        List<String> categories = items.stream()
                .flatMap(item -> item.categories().stream())
                .distinct()
                .sorted(TEXT_ORDER)
                .toList();
        int favoriteCount = Math.toIntExact(items.stream().filter(LibraryItem::favorite).count());
        return new LibraryOverview(cards, categories, favoriteCount);
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

    private static LibraryCard card(LibraryItem item) {
        return new LibraryCard(
                item.id(),
                item.title(),
                item.kind(),
                item.categories().stream().sorted(TEXT_ORDER).toList(),
                item.favorite(),
                item.progress());
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
                item.history().size());
    }

    private static LibraryHistoryRow historyRow(LibraryItem item, LibraryHistoryEntry entry) {
        return new LibraryHistoryRow(
                item.id(),
                item.title(),
                entry.contentId(),
                entry.openedAt(),
                entry.position());
    }
}
