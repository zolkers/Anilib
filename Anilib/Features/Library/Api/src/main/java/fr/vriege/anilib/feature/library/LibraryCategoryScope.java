package fr.vriege.anilib.feature.library;

import java.util.Objects;

public enum LibraryCategoryScope {
    ANIME,
    MANGA,
    SHARED;

    public boolean supports(MediaKind kind) {
        Objects.requireNonNull(kind, "kind must not be null");
        return switch (this) {
            case ANIME -> kind == MediaKind.ANIME;
            case MANGA -> kind != MediaKind.ANIME;
            case SHARED -> true;
        };
    }
}
