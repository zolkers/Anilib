package fr.vriege.anilib.feature.source;

import fr.vriege.anilib.foundation.validation.Preconditions;

import java.net.URI;
import java.util.Optional;

/** Source-owned title summary rendered by browse and global search. */
public record SourceCatalogueItem(
        SourceCatalogueItemId id,
        String title,
        String description,
        Optional<URI> thumbnail,
        SourceContentKind contentKind) {
    public SourceCatalogueItem {
        Preconditions.requireNonNull(id, "id");
        title = Preconditions.requireNonBlank(title, "title");
        description = Preconditions.requireNonNull(description, "description");
        thumbnail = Preconditions.requireNonNull(thumbnail, "thumbnail");
        thumbnail.ifPresent(SourceCatalogueItem::validateThumbnail);
        Preconditions.requireNonNull(contentKind, "contentKind");
    }

    private static void validateThumbnail(URI uri) {
        String scheme = uri.getScheme();
        if (scheme == null || !(scheme.equalsIgnoreCase("http")
                || scheme.equalsIgnoreCase("https")
                || scheme.equalsIgnoreCase("file"))) {
            throw new IllegalArgumentException("thumbnail must use http, https, or file");
        }
    }
}
