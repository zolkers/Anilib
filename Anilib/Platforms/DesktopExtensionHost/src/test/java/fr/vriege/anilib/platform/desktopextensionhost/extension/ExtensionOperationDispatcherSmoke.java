package fr.vriege.anilib.platform.desktopextensionhost.extension;

import fr.vriege.anilib.platform.desktopextensionhost.compat.aniyomi.animesource.model.SAnime;
import fr.vriege.anilib.platform.desktopextensionhost.compat.aniyomi.animesource.model.SEpisode;
import fr.vriege.anilib.platform.desktopextensionhost.compat.aniyomi.animesource.model.Video;
import fr.vriege.anilib.platform.desktopextensionhost.compat.aniyomi.animesource.model.AnimeFilter;
import fr.vriege.anilib.platform.desktopextensionhost.compat.aniyomi.animesource.model.AnimeFilterList;
import fr.vriege.anilib.platform.desktopextensionhost.compat.aniyomi.animesource.model.AnimesPage;
import fr.vriege.anilib.platform.desktopextensionhost.compat.aniyomi.animesource.online.AnimeHttpSource;
import fr.vriege.anilib.platform.desktopextensionhost.compat.aniyomi.source.model.Page;
import fr.vriege.anilib.platform.desktopextensionhost.compat.aniyomi.source.online.HttpSource;
import java.util.List;
import java.util.Map;
import kotlin.coroutines.Continuation;
import okhttp3.Request;
import okhttp3.Response;

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

        LazyImageSource imageSource = new LazyImageSource();
        Page page = new Page(0, "https://example.invalid/chapter/page-1", null, null);
        Request imageRequest = ExtensionSourceOperations.imageRequest(imageSource, page);
        if (!page.getImageUrl().equals("https://cdn.example.invalid/page-1.webp")
                || !imageRequest.url().toString().equals(page.getImageUrl())
                || !"https://example.invalid/chapter/page-1".equals(imageRequest.header("Referer"))) {
            throw new IllegalStateException("Lazy manga image URL/request dispatch failed");
        }
        verifyFilters();
    }

    private static void verifyFilters() {
        AnimeFilter.Text query = new AnimeFilter.Text("Title", "") { };
        AnimeFilter.CheckBox dubbed = new AnimeFilter.CheckBox("Dubbed", false) { };
        AnimeFilter.Select<String> genre = new AnimeFilter.Select<>(
                "Genre", new String[]{"All", "Action"}, 0) { };
        AnimeFilter.TriState completed = new AnimeFilter.TriState("Completed", 0) { };
        AnimeFilter.Group<AnimeFilter<?>> status = new AnimeFilter.Group<>("Status", List.of(completed)) { };
        AnimeFilter.Sort sort = new AnimeFilter.Sort(
                "Sort",
                new String[]{"Popular", "Newest"},
                new AnimeFilter.Sort.Selection(0, true)) { };
        ExtensionSourceFilterCodec.FilterSet filters = ExtensionSourceFilterCodec.from(
                new AnimeFilterList(query, dubbed, genre, status, sort));
        filters.apply(Map.of(
                "filter.0", "wanted",
                "filter.1", "true",
                "filter.2", "Action",
                "filter.3.0", "include",
                "filter.4", "↓ Newest"));
        AnimeFilter.Sort.Selection selection = sort.getState();
        if (filters.definitions().size() != 6
                || !query.getState().equals("wanted")
                || !dubbed.getState()
                || genre.getState() != 1
                || completed.getState() != AnimeFilter.TriState.STATE_INCLUDE
                || selection.getIndex() != 1
                || selection.getAscending()) {
            throw new IllegalStateException("Dynamic extension filters did not round-trip through the host codec");
        }
    }

    private static final class LazyImageSource extends HttpSource {
        @Override public String getBaseUrl() { return "https://example.invalid"; }
        @Override public String getName() { return "Lazy images"; }
        @Override public String getLang() { return "en"; }
        @Override public boolean getSupportsLatest() { return false; }

        public Object getImageUrl(Page page, Continuation<? super String> continuation) {
            return "https://cdn.example.invalid/page-1.webp";
        }

        @Override protected Request imageRequest(Page page) {
            return new Request.Builder()
                    .url(page.getImageUrl())
                    .header("Referer", page.getUrl())
                    .build();
        }

        @Override protected String imageUrlParse(Response response) {
            throw new AssertionError("The modern image URL operation should be preferred");
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

        public Object getPopularAnime(int page, Continuation<? super AnimesPage> continuation) {
            return null;
        }

        @Override public Object getSearchAnime(
                int page,
                String query,
                AnimeFilterList filters,
                Continuation<? super AnimesPage> continuation) {
            return null;
        }

        public Object getAnimeDetails(SAnime anime, Continuation<? super SAnime> continuation) {
            return anime;
        }

        public Object getVideoList(SEpisode episode, Continuation<? super List<Video>> continuation) {
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
