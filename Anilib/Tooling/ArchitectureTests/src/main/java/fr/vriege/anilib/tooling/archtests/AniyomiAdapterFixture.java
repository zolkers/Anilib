package fr.vriege.anilib.tooling.archtests;

import java.util.List;
import java.util.Map;

/** Public reflection fixture for the dependency-free Aniyomi source adapter checks. */
public final class AniyomiAdapterFixture {
    private AniyomiAdapterFixture() {
    }

    public static final class Source {
        public long getId() {
            return 42L;
        }

        public String getName() {
            return "Example";
        }

        public String getLang() {
            return "en";
        }

        public boolean getSupportsLatest() {
            return true;
        }

        public Object getFilterList() {
            return new Object();
        }

        public Observable<Page> fetchPopularAnime(int page) {
            return new Observable<>(new Page(List.of(new Anime()), true));
        }

        public Observable<Page> fetchLatestUpdates(int page) {
            return fetchPopularAnime(page);
        }

        public Observable<Page> fetchSearchAnime(int page, String query, Object filters) {
            return fetchPopularAnime(page);
        }

        public Observable<List<Episode>> fetchEpisodeList(Anime anime) {
            return new Observable<>(List.of(new Episode()));
        }

        public Observable<List<Video>> fetchVideoList(Episode episode) {
            return new Observable<>(List.of(new Video()));
        }
    }

    public static final class ModernSource {
        private boolean filterApplied;

        public long getId() {
            return 84L;
        }

        public String getName() {
            return "Modern Example";
        }

        public String getLang() {
            return "fr";
        }

        public boolean getSupportsLatest() {
            return true;
        }

        public Object getFilterList() {
            return List.of(
                    new AnimeFilter.Text("Title", ""),
                    new AnimeFilter.CheckBox("Dubbed", false),
                    new AnimeFilter.Select("Order", new String[]{"Newest", "Oldest"}, 0),
                    new AnimeFilter.Group(
                            "Genres",
                            List.of(new AnimeFilter.TriState("Action", 0))));
        }

        public Page getPopularAnime(int page, Object continuation) {
            return new Page(List.of(new Anime()), true);
        }

        public Page getLatestUpdates(int page, Object continuation) {
            return getPopularAnime(page, continuation);
        }

        public Page getSearchAnime(int page, String query, Object filters, Object continuation) {
            List<?> values = (List<?>) filters;
            filterApplied = ((AnimeFilter.Text) values.get(0)).getState().equals("wanted")
                    && ((AnimeFilter.CheckBox) values.get(1)).getState()
                    && ((AnimeFilter.Select) values.get(2)).getState() == 1
                    && ((AnimeFilter.TriState) ((AnimeFilter.Group) values.get(3)).getState().getFirst())
                    .getState() == 1;
            return getPopularAnime(page, continuation);
        }

        public boolean filterApplied() {
            return filterApplied;
        }

        public EpisodeUpdate getAnimeEpisodeUpdate(
                Anime anime,
                List<Episode> episodes,
                boolean fetchDetails,
                boolean fetchEpisodes,
                Object continuation) {
            return new EpisodeUpdate(anime, List.of(new Episode()));
        }

        public List<Hoster> getHosterList(Episode episode, Object continuation) {
            return List.of(new Hoster());
        }

        public List<Video> getVideoList(Hoster hoster, Object continuation) {
            return List.of(new Video());
        }
    }

    public record EpisodeUpdate(Anime anime, List<Episode> episodes) {
        public List<Episode> getEpisodes() {
            return episodes;
        }
    }

    public static final class Hoster {
        public List<Video> getVideoList() {
            return null;
        }
    }

    public abstract static class AnimeFilter<T> {
        private final String name;
        private T state;

        protected AnimeFilter(String name, T state) {
            this.name = name;
            this.state = state;
        }

        public String getName() {
            return name;
        }

        public T getState() {
            return state;
        }

        public void setState(T state) {
            this.state = state;
        }

        public static final class Text extends AnimeFilter<String> {
            public Text(String name, String state) {
                super(name, state);
            }
        }

        public static final class CheckBox extends AnimeFilter<Boolean> {
            public CheckBox(String name, boolean state) {
                super(name, state);
            }
        }

        public static final class TriState extends AnimeFilter<Integer> {
            public TriState(String name, int state) {
                super(name, state);
            }
        }

        public static final class Select extends AnimeFilter<Integer> {
            private final String[] values;

            public Select(String name, String[] values, int state) {
                super(name, state);
                this.values = values.clone();
            }

            public String[] getValues() {
                return values.clone();
            }
        }

        public static final class Group extends AnimeFilter<List<AnimeFilter<?>>> {
            public Group(String name, List<AnimeFilter<?>> state) {
                super(name, List.copyOf(state));
            }
        }
    }

    public record Page(List<Anime> animes, boolean hasNextPage) {
        public List<Anime> getAnimes() {
            return animes;
        }

        public boolean getHasNextPage() {
            return hasNextPage;
        }
    }

    public static final class Anime {
        public String getUrl() {
            return "/anime/example";
        }

        public String getTitle() {
            return "Example Anime";
        }

        public String getDescription() {
            return "Example description";
        }

        public String getThumbnail_url() {
            return "https://example.test/cover.jpg";
        }
    }

    public static final class Episode {
        public String getUrl() {
            return "/episode/1";
        }

        public String getName() {
            return "Episode 1";
        }

        public float getEpisode_number() {
            return 1.0f;
        }

        public long getDate_upload() {
            return 1_700_000_000_000L;
        }

        public String getScanlator() {
            return "Example";
        }
    }

    public static final class Video {
        public String getVideoUrl() {
            return "https://cdn.example.test/video.m3u8";
        }

        public String getVideoTitle() {
            return "1080p";
        }

        public Headers getHeaders() {
            return new Headers();
        }

        public List<Track> getSubtitleTracks() {
            return List.of(new Track());
        }
    }

    public static final class Headers {
        public Map<String, List<String>> toMultimap() {
            return Map.of("Referer", List.of("https://example.test/"));
        }
    }

    public static final class Track {
        public String getUrl() {
            return "https://cdn.example.test/subtitles.vtt";
        }

        public String getLang() {
            return "en";
        }
    }

    public static final class Observable<T> {
        private final T value;

        public Observable(T value) {
            this.value = value;
        }

        public Blocking<T> toBlocking() {
            return new Blocking<>(value);
        }
    }

    public static final class Blocking<T> {
        private final T value;

        public Blocking(T value) {
            this.value = value;
        }

        public T single() {
            return value;
        }
    }
}
