package fr.vriege.anilib.platform.desktopextensionhost.extension;

import fr.vriege.anilib.platform.desktopextensionhost.compat.aniyomi.animesource.model.SAnime;
import fr.vriege.anilib.platform.desktopextensionhost.compat.aniyomi.animesource.model.SEpisode;
import fr.vriege.anilib.platform.desktopextensionhost.compat.aniyomi.animesource.model.AnimeFilterList;
import fr.vriege.anilib.platform.desktopextensionhost.compat.aniyomi.animesource.model.AnimesPage;
import fr.vriege.anilib.platform.desktopextensionhost.compat.aniyomi.animesource.online.AnimeHttpSource;
import java.util.List;
import kotlin.coroutines.Continuation;

public final class ExtensionOperationDispatcherSmoke {
    private ExtensionOperationDispatcherSmoke() {
    }

    public static void verify() {
        ModernSource source = new ModernSource();
        SAnime anime = SAnime.Companion.create();
        anime.setUrl("/title");
        anime.setTitle("Retained title");
        var modern = ExtensionOperationDispatcher.modernOrRx(
                source, "getEpisodeList", "fetchEpisodeList", anime);
        List<SEpisode> episodes = ExtensionOperationDispatcher.listResult(modern.value(), SEpisode.class);
        if (!modern.available() || episodes.size() != 1 || !episodes.getFirst().getName().equals("Episode 1")) {
            throw new IllegalStateException("Modern suspend source dispatch failed");
        }
        if (ExtensionOperationDispatcher.hasClassicImplementation(source, "episodeListParse", (Object) null)) {
            throw new IllegalStateException("Inherited compatibility placeholder was treated as an implementation");
        }
        ExtensionCompatibility.requireSupported(ExtensionKind.ANIME, source);

        var reactive = ExtensionOperationDispatcher.modernOrRx(
                new ReactiveSource(), "getPageList", "fetchPageList", "chapter");
        if (!reactive.available() || !reactive.value().equals(List.of("page"))) {
            throw new IllegalStateException("Reactive source fallback dispatch failed");
        }
    }

    private static final class ModernSource extends AnimeHttpSource {
        @Override public String getBaseUrl() { return "https://example.invalid"; }
        @Override public String getName() { return "Modern"; }
        @Override public String getLang() { return "en"; }
        @Override public boolean getSupportsLatest() { return false; }

        public Object getEpisodeList(SAnime anime, Continuation<? super List<SEpisode>> continuation) {
            if (!anime.getTitle().equals("Retained title")) {
                throw new IllegalStateException("Catalogue model was not retained");
            }
            SEpisode episode = SEpisode.Companion.create();
            episode.setUrl("/episode-1");
            episode.setName("Episode 1");
            return List.of(episode);
        }

        public Object getPopularAnime(int page, Continuation<Object> continuation) {
            return null;
        }

        @Override public Object getSearchAnime(
                int page,
                String query,
                AnimeFilterList filters,
                Continuation<? super AnimesPage> continuation) {
            return null;
        }

        public Object getAnimeDetails(SAnime anime, Continuation<Object> continuation) {
            return anime;
        }

        public Object getVideoList(SEpisode episode, Continuation<Object> continuation) {
            return List.of();
        }
    }

    private static final class ReactiveSource {
        public FakeObservable fetchPageList(String chapter) {
            return new FakeObservable(List.of(chapter.equals("chapter") ? "page" : "unexpected"));
        }
    }

    private record FakeObservable(Object value) {
        public FakeBlocking toBlocking() {
            return new FakeBlocking(value);
        }
    }

    private record FakeBlocking(Object value) {
        public Object single() {
            return value;
        }
    }
}
