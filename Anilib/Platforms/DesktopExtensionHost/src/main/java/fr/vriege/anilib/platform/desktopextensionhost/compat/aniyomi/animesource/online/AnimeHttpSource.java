package fr.vriege.anilib.platform.desktopextensionhost.compat.aniyomi.animesource.online;

import fr.vriege.anilib.platform.desktopextensionhost.compat.aniyomi.animesource.AnimeCatalogueSource;
import fr.vriege.anilib.platform.desktopextensionhost.compat.aniyomi.animesource.model.AnimeFilterList;
import fr.vriege.anilib.platform.desktopextensionhost.compat.aniyomi.animesource.model.AnimesPage;
import fr.vriege.anilib.platform.desktopextensionhost.compat.aniyomi.animesource.model.Hoster;
import fr.vriege.anilib.platform.desktopextensionhost.compat.aniyomi.animesource.model.SAnime;
import fr.vriege.anilib.platform.desktopextensionhost.compat.aniyomi.animesource.model.SEpisode;
import fr.vriege.anilib.platform.desktopextensionhost.compat.aniyomi.animesource.model.Video;
import fr.vriege.anilib.platform.desktopextensionhost.compat.aniyomi.network.NetworkHelper;
import fr.vriege.anilib.platform.desktopextensionhost.compat.aniyomi.network.OkHttpExtensionsKt;
import fr.vriege.anilib.platform.desktopextensionhost.compat.aniyomi.network.RequestsKt;
import kotlin.coroutines.Continuation;
import okhttp3.Headers;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import rx.Observable;

import java.nio.ByteBuffer;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.List;

public abstract class AnimeHttpSource implements AnimeCatalogueSource {
    private final NetworkHelper network = NetworkHelper.shared();
    private Long id;
    private Headers headers;

    public AnimeHttpSource() {
    }

    public abstract String getBaseUrl();

    protected final NetworkHelper getNetwork() { return network; }

    public final Headers getHeaders() {
        if (headers == null) {
            headers = headersBuilder().build();
        }
        return headers;
    }

    public OkHttpClient getClient() { return network.getClient(); }

    protected Request popularAnimeRequest(int page) { throw unsupported("popular anime"); }
    protected AnimesPage popularAnimeParse(Response response) { throw unsupported("popular anime"); }
    public Object getPopularAnime(int page, Continuation<? super AnimesPage> continuation) {
        return execute(popularAnimeRequest(page), this::popularAnimeParse);
    }
    public Observable<AnimesPage> fetchPopularAnime(int page) {
        return observe(popularAnimeRequest(page), this::popularAnimeParse);
    }
    protected Request searchAnimeRequest(int page, String query, AnimeFilterList filters) {
        throw unsupported("anime search");
    }
    protected AnimesPage searchAnimeParse(Response response) { throw unsupported("anime search"); }
    public Object getSearchAnime(
            int page,
            String query,
            AnimeFilterList filters,
            Continuation<? super AnimesPage> continuation) {
        return execute(searchAnimeRequest(page, query, filters), this::searchAnimeParse);
    }
    public Observable<AnimesPage> fetchSearchAnime(int page, String query, AnimeFilterList filters) {
        return observe(searchAnimeRequest(page, query, filters), this::searchAnimeParse);
    }
    protected Request latestUpdatesRequest(int page) { throw unsupported("latest anime"); }
    protected AnimesPage latestUpdatesParse(Response response) { throw unsupported("latest anime"); }
    public Object getLatestUpdates(int page, Continuation<? super AnimesPage> continuation) {
        return execute(latestUpdatesRequest(page), this::latestUpdatesParse);
    }
    public Observable<AnimesPage> fetchLatestUpdates(int page) {
        return observe(latestUpdatesRequest(page), this::latestUpdatesParse);
    }
    public Request animeDetailsRequest(SAnime anime) {
        return RequestsKt.GET(getBaseUrl() + anime.getUrl(), getHeaders(), null);
    }
    protected SAnime animeDetailsParse(Response response) { throw unsupported("anime details"); }
    public Object getAnimeDetails(SAnime anime, Continuation<? super SAnime> continuation) {
        SAnime details = execute(animeDetailsRequest(anime), this::animeDetailsParse);
        details.setInitialized(true);
        return details;
    }
    public Observable<SAnime> fetchAnimeDetails(SAnime anime) {
        return observe(animeDetailsRequest(anime), response -> {
            SAnime details = animeDetailsParse(response);
            details.setInitialized(true);
            return details;
        });
    }
    protected Request episodeListRequest(SAnime anime) {
        return RequestsKt.GET(getBaseUrl() + anime.getUrl(), getHeaders(), null);
    }
    protected List<SEpisode> episodeListParse(Response response) { throw unsupported("anime episodes"); }
    protected SEpisode episodeVideoParse(Response response) { throw unsupported("anime episode video"); }
    public Object getEpisodeList(SAnime anime, Continuation<? super List<SEpisode>> continuation) {
        return execute(episodeListRequest(anime), this::episodeListParse);
    }
    public Observable<List<SEpisode>> fetchEpisodeList(SAnime anime) {
        return observe(episodeListRequest(anime), this::episodeListParse);
    }
    protected Request seasonListRequest(SAnime anime) {
        return RequestsKt.GET(getBaseUrl() + anime.getUrl(), getHeaders(), null);
    }
    protected List<SAnime> seasonListParse(Response response) { throw unsupported("anime seasons"); }
    public Object getSeasonList(SAnime anime, Continuation<? super List<SAnime>> continuation) {
        return execute(seasonListRequest(anime), this::seasonListParse);
    }
    protected Request videoListRequest(SEpisode episode) {
        return RequestsKt.GET(getBaseUrl() + episode.getUrl(), getHeaders(), null);
    }
    protected List<Video> videoListParse(Response response) { throw unsupported("anime videos"); }
    public Object getVideoList(SEpisode episode, Continuation<? super List<Video>> continuation) {
        return execute(videoListRequest(episode), this::videoListParse);
    }
    public Observable<List<Video>> fetchVideoList(SEpisode episode) {
        return observe(videoListRequest(episode), this::videoListParse);
    }
    protected Request hosterListRequest(SEpisode episode) {
        return RequestsKt.GET(getBaseUrl() + episode.getUrl(), getHeaders(), null);
    }
    protected List<Hoster> hosterListParse(Response response) { throw unsupported("anime hosters"); }
    public Object getHosterList(SEpisode episode, Continuation<? super List<Hoster>> continuation) {
        return execute(hosterListRequest(episode), this::hosterListParse);
    }
    protected Request videoListRequest(Hoster hoster) {
        return RequestsKt.GET(hoster.getHosterUrl(), getHeaders(), null);
    }
    protected List<Video> videoListParse(Response response, Hoster hoster) {
        throw unsupported("hoster videos");
    }
    public Object getVideoList(Hoster hoster, Continuation<? super List<Video>> continuation) {
        return execute(videoListRequest(hoster), response -> videoListParse(response, hoster));
    }
    public Object resolveVideo(Video video, Continuation<? super Video> continuation) { return video; }
    protected Request videoUrlRequest(Video video) {
        return RequestsKt.GET(video.getUrl(), getHeaders(), null);
    }
    protected String videoUrlParse(Response response) { throw unsupported("anime video URL"); }
    public Object getVideoUrl(Video video, Continuation<? super String> continuation) {
        return execute(videoUrlRequest(video), this::videoUrlParse);
    }
    public Observable<String> fetchVideoUrl(Video video) {
        return observe(videoUrlRequest(video), this::videoUrlParse);
    }
    protected List<Video> sort(List<Video> videos) { return videos; }
    public List<Video> sortVideos(List<Video> videos) { return sort(videos); }
    public List<Hoster> sortHosters(List<Hoster> hosters) { return hosters; }
    public AnimeFilterList getFilterList() { return new AnimeFilterList(); }
    public String getAnimeUrl(SAnime anime) { return animeDetailsRequest(anime).url().toString(); }
    public String getEpisodeUrl(SEpisode episode) { return episode.getUrl(); }
    protected void setUrlWithoutDomain(SAnime anime, String url) {
        anime.setUrl(getUrlWithoutDomain(url));
    }
    protected void setUrlWithoutDomain(SEpisode episode, String url) {
        episode.setUrl(getUrlWithoutDomain(url));
    }
    public void prepareNewEpisode(SEpisode episode, SAnime anime) { }
    protected String getUrlWithoutDomain(String url) {
        URI value = URI.create(url);
        if (!value.isAbsolute()) {
            return url;
        }
        String result = value.getRawPath();
        if (value.getRawQuery() != null) result += '?' + value.getRawQuery();
        if (value.getRawFragment() != null) result += '#' + value.getRawFragment();
        return result;
    }

    protected Headers.Builder headersBuilder() {
        return new Headers.Builder().add("User-Agent", network.defaultUserAgentProvider());
    }

    public String getHomeUrl() {
        return getBaseUrl();
    }

    public int getVersionId() {
        return 1;
    }

    @Override
    public long getId() {
        if (id == null) {
            id = generateId(getName(), getLang(), getVersionId());
        }
        return id;
    }

    protected long generateId(String name, String language, int versionId) {
        String key = name.toLowerCase(Locale.ROOT) + '/' + language + '/' + versionId;
        try {
            byte[] digest = MessageDigest.getInstance("MD5").digest(key.getBytes(StandardCharsets.UTF_8));
            return ByteBuffer.wrap(digest).getLong() & Long.MAX_VALUE;
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("MD5 digest is unavailable", exception);
        }
    }

    @Override
    public String toString() {
        return getName() + " (" + getLang().toUpperCase(Locale.ROOT) + ')';
    }

    private static UnsupportedOperationException unsupported(String operation) {
        return new UnsupportedOperationException("Source does not implement " + operation);
    }

    private <T> T execute(Request request, ResponseParser<T> parser) {
        try (Response response = (Response) OkHttpExtensionsKt.awaitSuccess(
                getClient().newCall(request), null)) {
            return parser.parse(response);
        }
    }

    private <T> Observable<T> observe(Request request, ResponseParser<T> parser) {
        return OkHttpExtensionsKt.asObservableSuccess(getClient().newCall(request))
                .map(response -> {
                    try (response) {
                        return parser.parse(response);
                    }
                });
    }

    @FunctionalInterface
    private interface ResponseParser<T> {
        T parse(Response response);
    }
}
