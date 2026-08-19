package fr.vriege.anilib.platform.desktopextensionhost.compat.aniyomi.animesource.model;

import java.util.List;
import java.util.Objects;

public final class AnimesPage {
    private final List<SAnime> animes;
    private final boolean hasNextPage;

    public AnimesPage(List<? extends SAnime> animes, boolean hasNextPage) {
        this.animes = List.copyOf(animes);
        this.hasNextPage = hasNextPage;
    }

    public List<SAnime> getAnimes() { return animes; }
    public boolean getHasNextPage() { return hasNextPage; }
    public List<SAnime> component1() { return animes; }
    public boolean component2() { return hasNextPage; }
    public AnimesPage copy(List<? extends SAnime> values, boolean nextPage) {
        return new AnimesPage(values, nextPage);
    }

    @Override public boolean equals(Object value) {
        return value instanceof AnimesPage other
                && hasNextPage == other.hasNextPage
                && animes.equals(other.animes);
    }

    @Override public int hashCode() { return Objects.hash(animes, hasNextPage); }
    @Override public String toString() { return "AnimesPage(animes=" + animes + ", hasNextPage=" + hasNextPage + ')'; }
}
