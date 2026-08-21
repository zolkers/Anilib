package fr.vriege.anilib.platform.desktopextensionhost;

import fr.vriege.anilib.platform.desktopextensionhost.compat.aniyomi.animesource.model.AnimesPage;
import fr.vriege.anilib.platform.desktopextensionhost.compat.aniyomi.animesource.model.FetchType;
import fr.vriege.anilib.platform.desktopextensionhost.compat.aniyomi.animesource.model.SAnime;
import fr.vriege.anilib.platform.desktopextensionhost.compat.aniyomi.animesource.model.SEpisode;
import fr.vriege.anilib.platform.desktopextensionhost.compat.aniyomi.source.model.MangasPage;
import fr.vriege.anilib.platform.desktopextensionhost.compat.aniyomi.source.model.SChapter;
import fr.vriege.anilib.platform.desktopextensionhost.compat.aniyomi.source.model.SManga;
import fr.vriege.anilib.platform.desktopextensionhost.compat.aniyomi.source.model.SMangaUpdate;
import fr.vriege.anilib.platform.desktopextensionhost.compat.aniyomi.source.online.HttpSource;
import okhttp3.Headers;

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
        SMangaUpdate update = new SMangaUpdate(manga, List.of(chapter));
        if (update.getManga() != manga || !update.getChapters().equals(List.of(chapter))) {
            throw new IllegalStateException("Combined manga update ABI is invalid");
        }
        verifyReflectiveHeadersDelegate();

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

    private static void verifyReflectiveHeadersDelegate() {
        HttpSource source = new HttpSource() {
            @Override
            public String getBaseUrl() {
                return "https://example.invalid";
            }

            @Override
            public String getName() {
                return "Reflective headers";
            }

            @Override
            public boolean getSupportsLatest() {
                return false;
            }
        };
        try {
            var field = HttpSource.class.getDeclaredField("headers$delegate");
            field.setAccessible(true);
            field.set(source, new ReflectiveHeaders(new Headers.Builder()
                    .add("X-Anilib-Compatibility", "available")
                    .build()));
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Reflective HTTP headers ABI is invalid", exception);
        }
        if (!"available".equals(source.getHeaders().get("X-Anilib-Compatibility"))) {
            throw new IllegalStateException("Reflective HTTP headers delegate was ignored");
        }
    }

    public record ReflectiveHeaders(Headers value) {
        public Headers getValue() {
            return value;
        }
    }
}
