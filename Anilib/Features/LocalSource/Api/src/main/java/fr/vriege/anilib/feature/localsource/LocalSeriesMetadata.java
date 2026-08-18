package fr.vriege.anilib.feature.localsource;

import fr.vriege.anilib.foundation.validation.Preconditions;

import java.util.List;
import java.util.Optional;

public record LocalSeriesMetadata(
        String title,
        Optional<String> author,
        Optional<String> artist,
        String description,
        List<String> genres,
        LocalSeriesStatus status) {
    public LocalSeriesMetadata {
        title = Preconditions.requireNonBlank(title, "title");
        author = text(author, "author");
        artist = text(artist, "artist");
        description = Preconditions.requireNonNull(description, "description").strip();
        genres = List.copyOf(Preconditions.requireNonNull(genres, "genres")).stream()
                .map(String::strip)
                .filter(value -> !value.isEmpty())
                .distinct()
                .toList();
        status = Preconditions.requireNonNull(status, "status");
    }

    public static LocalSeriesMetadata defaults(String title) {
        return new LocalSeriesMetadata(
                title,
                Optional.empty(),
                Optional.empty(),
                "",
                List.of(),
                LocalSeriesStatus.UNKNOWN);
    }

    private static Optional<String> text(Optional<String> value, String name) {
        return Preconditions.requireNonNull(value, name)
                .map(String::strip)
                .filter(text -> !text.isEmpty());
    }
}
