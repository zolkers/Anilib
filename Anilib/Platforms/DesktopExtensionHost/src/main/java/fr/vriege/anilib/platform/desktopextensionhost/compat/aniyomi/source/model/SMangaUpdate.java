package fr.vriege.anilib.platform.desktopextensionhost.compat.aniyomi.source.model;

import java.util.List;
import java.util.Objects;

public final class SMangaUpdate {
    private final SManga manga;
    private final List<SChapter> chapters;

    public SMangaUpdate(SManga manga, List<SChapter> chapters) {
        this.manga = Objects.requireNonNull(manga, "manga");
        this.chapters = Objects.requireNonNull(chapters, "chapters");
    }

    public SManga getManga() {
        return manga;
    }

    public List<SChapter> getChapters() {
        return chapters;
    }
}
