package fr.vriege.anilib.platform.desktopextensionhost;

import fr.vriege.anilib.platform.desktopextensionhost.compat.aniyomi.animesource.model.AnimesPage;
import fr.vriege.anilib.platform.desktopextensionhost.compat.aniyomi.animesource.model.FetchType;
import fr.vriege.anilib.platform.desktopextensionhost.compat.aniyomi.animesource.model.SAnime;
import fr.vriege.anilib.platform.desktopextensionhost.compat.aniyomi.animesource.model.SEpisode;
import fr.vriege.anilib.platform.desktopextensionhost.compat.aniyomi.source.model.MangasPage;
import fr.vriege.anilib.platform.desktopextensionhost.compat.aniyomi.source.model.SChapter;
import fr.vriege.anilib.platform.desktopextensionhost.compat.aniyomi.source.model.SManga;

import java.util.List;

final class ExtensionSourceModelSmoke {
    private ExtensionSourceModelSmoke() {
    }

    static void verify() {
        SManga manga = SManga.Companion.create();
        manga.setUrl("/manga");
        manga.setTitle("Manga");
        manga.setGenre("Action, Adventure");
        if (!manga.getGenres().equals(List.of("Action", "Adventure"))
                || !new MangasPage(List.of(manga), true).getHasNextPage()) {
            throw new IllegalStateException("Manga source ABI is invalid");
        }

        SChapter chapter = SChapter.Companion.create();
        chapter.setUrl("/chapter");
        chapter.setName("Chapter 1");
        if (chapter.getChapter_number() != -1.0f) {
            throw new IllegalStateException("Chapter source ABI defaults are invalid");
        }

        SAnime anime = SAnime.Companion.create();
        anime.setUrl("/anime");
        anime.setTitle("Anime");
        if (anime.getFetch_type() != FetchType.Episodes
                || !new AnimesPage(List.of(anime), false).getAnimes().equals(List.of(anime))) {
            throw new IllegalStateException("Anime source ABI is invalid");
        }

        SEpisode episode = SEpisode.Companion.create();
        episode.setUrl("/episode");
        episode.setName("Episode 1");
        if (episode.getEpisode_number() != -1.0f || episode.getMemo() == null) {
            throw new IllegalStateException("Episode source ABI defaults are invalid");
        }
    }
}
