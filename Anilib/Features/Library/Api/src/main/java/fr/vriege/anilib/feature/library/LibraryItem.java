package fr.vriege.anilib.feature.library;

import fr.vriege.anilib.foundation.validation.Preconditions;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public record LibraryItem(
        LibraryItemId id,
        String title,
        MediaKind kind,
        Instant addedAt,
        Set<String> categories,
        boolean favorite,
        Optional<LibraryProgress> progress,
        List<LibraryHistoryEntry> history,
        LibraryTitleMetadata metadata,
        Optional<LibraryOrigin> origin) {

    public LibraryItem {
        Preconditions.requireNonNull(id, "id");
        Preconditions.requireNonBlank(title, "title");
        Preconditions.requireNonNull(kind, "kind");
        Preconditions.requireNonNull(addedAt, "addedAt");
        categories = Set.copyOf(categories);
        if (categories.stream().anyMatch(String::isBlank)) {
            throw new IllegalArgumentException("categories must not contain blank values");
        }
        Preconditions.requireNonNull(progress, "progress");
        history = List.copyOf(history);
        Preconditions.requireNonNull(metadata, "metadata");
        Preconditions.requireNonNull(origin, "origin");
    }

    public LibraryItem(
            LibraryItemId id,
            String title,
            MediaKind kind,
            Instant addedAt,
            Set<String> categories,
            boolean favorite,
            Optional<LibraryProgress> progress,
            List<LibraryHistoryEntry> history,
            LibraryTitleMetadata metadata) {
        this(id, title, kind, addedAt, categories, favorite, progress, history, metadata, Optional.empty());
    }

    public LibraryItem(
            LibraryItemId id,
            String title,
            MediaKind kind,
            Instant addedAt,
            Set<String> categories) {
        this(
                id,
                title,
                kind,
                addedAt,
                categories,
                false,
                Optional.empty(),
                List.of(),
                LibraryTitleMetadata.empty(),
                Optional.empty());
    }

    public static LibraryItem create(String title, MediaKind kind) {
        return new LibraryItem(LibraryItemId.create(), title, kind, Instant.now(), Set.of());
    }

    public LibraryItem withCategories(Set<String> nextCategories) {
        return copy(nextCategories, favorite, progress, history, metadata);
    }

    public LibraryItem withFavorite(boolean nextFavorite) {
        return copy(categories, nextFavorite, progress, history, metadata);
    }

    public LibraryItem withProgress(LibraryProgress nextProgress) {
        return copy(categories, favorite, Optional.of(nextProgress), history, metadata);
    }

    public LibraryItem withoutProgress() {
        return copy(categories, favorite, Optional.empty(), history, metadata);
    }

    public LibraryItem recordHistory(LibraryHistoryEntry entry) {
        LibraryHistoryEntry selectedEntry = Preconditions.requireNonNull(entry, "entry");
        List<LibraryHistoryEntry> nextHistory = history.stream()
                .filter(existing -> !existing.contentId().equals(selectedEntry.contentId()))
                .collect(Collectors.toCollection(ArrayList::new));
        nextHistory.add(selectedEntry);
        return copy(categories, favorite, progress, nextHistory, metadata);
    }

    public LibraryItem withoutHistoryEntry(String contentId, Instant openedAt) {
        Preconditions.requireNonBlank(contentId, "contentId");
        Preconditions.requireNonNull(openedAt, "openedAt");
        List<LibraryHistoryEntry> nextHistory = history.stream()
                .filter(entry -> !entry.contentId().equals(contentId)
                        || !entry.openedAt().equals(openedAt))
                .toList();
        return copy(categories, favorite, progress, nextHistory, metadata);
    }

    public LibraryItem withMetadata(LibraryTitleMetadata nextMetadata) {
        return copy(categories, favorite, progress, history, nextMetadata);
    }

    public LibraryItem withTitleAndMetadata(
            String nextTitle,
            LibraryTitleMetadata nextMetadata) {
        return new LibraryItem(
                id,
                Preconditions.requireNonBlank(nextTitle, "nextTitle"),
                kind,
                addedAt,
                categories,
                favorite,
                progress,
                history,
                Preconditions.requireNonNull(nextMetadata, "nextMetadata"),
                origin);
    }

    public LibraryItem migratedTo(
            String nextTitle,
            LibraryOrigin nextOrigin,
            LibraryTitleMetadata nextMetadata) {
        return new LibraryItem(
                id,
                Preconditions.requireNonBlank(nextTitle, "nextTitle"),
                kind,
                addedAt,
                categories,
                favorite,
                progress,
                history,
                Preconditions.requireNonNull(nextMetadata, "nextMetadata"),
                Optional.of(Preconditions.requireNonNull(nextOrigin, "nextOrigin")));
    }

    public LibraryItem withOrigin(LibraryOrigin nextOrigin) {
        return new LibraryItem(
                id,
                title,
                kind,
                addedAt,
                categories,
                favorite,
                progress,
                history,
                metadata,
                Optional.of(Preconditions.requireNonNull(nextOrigin, "nextOrigin")));
    }

    private LibraryItem copy(
            Set<String> nextCategories,
            boolean nextFavorite,
            Optional<LibraryProgress> nextProgress,
            List<LibraryHistoryEntry> nextHistory,
            LibraryTitleMetadata nextMetadata) {
        return new LibraryItem(
                id,
                title,
                kind,
                addedAt,
                nextCategories,
                nextFavorite,
                nextProgress,
                nextHistory,
                nextMetadata,
                origin);
    }
}
