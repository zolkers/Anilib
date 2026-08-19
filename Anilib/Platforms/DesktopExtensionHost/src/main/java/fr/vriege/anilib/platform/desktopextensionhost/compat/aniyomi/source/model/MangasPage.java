package fr.vriege.anilib.platform.desktopextensionhost.compat.aniyomi.source.model;

import java.util.List;
import java.util.Objects;

public final class MangasPage {
    private final List<SManga> mangas;
    private final boolean hasNextPage;

    public MangasPage(List<? extends SManga> mangas, boolean hasNextPage) {
        this.mangas = List.copyOf(mangas);
        this.hasNextPage = hasNextPage;
    }

    public List<SManga> getMangas() { return mangas; }
    public boolean getHasNextPage() { return hasNextPage; }
    public List<SManga> component1() { return mangas; }
    public boolean component2() { return hasNextPage; }
    public MangasPage copy(List<? extends SManga> values, boolean nextPage) {
        return new MangasPage(values, nextPage);
    }

    @Override public boolean equals(Object value) {
        return value instanceof MangasPage other
                && hasNextPage == other.hasNextPage
                && mangas.equals(other.mangas);
    }

    @Override public int hashCode() { return Objects.hash(mangas, hasNextPage); }
    @Override public String toString() { return "MangasPage(mangas=" + mangas + ", hasNextPage=" + hasNextPage + ')'; }
}
