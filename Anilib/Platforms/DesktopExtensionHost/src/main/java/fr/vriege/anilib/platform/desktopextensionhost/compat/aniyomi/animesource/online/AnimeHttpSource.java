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
    protected Request searchAnimeRequest(int page, String query, AnimeFilterList filters) {
        throw unsupported("anime search");
    }
    protected AnimesPage searchAnimeParse(Response response) { throw unsupported("anime search"); }
    public Object getSearchAnime(
            int page,
            String query,
            AnimeFilterList filters,
            Continuation<? super AnimesPage> continuation) {
        Request request = searchAnimeRequest(page, query, filters);
        try (Response response = (Response) OkHttpExtensionsKt.awaitSuccess(
                getClient().newCall(request), null)) {
            return searchAnimeParse(response);
        }
    }
    protected Request latestUpdatesRequest(int page) { throw unsupported("latest anime"); }
    protected AnimesPage latestUpdatesParse(Response response) { throw unsupported("latest anime"); }
    public Request animeDetailsRequest(SAnime anime) {
        return RequestsKt.GET(getBaseUrl() + anime.getUrl(), getHeaders(), null);
    }
    protected SAnime animeDetailsParse(Response response) { throw unsupported("anime details"); }
    protected Request episodeListRequest(SAnime anime) {
        return RequestsKt.GET(getBaseUrl() + anime.getUrl(), getHeaders(), null);
    }
    protected List<SEpisode> episodeListParse(Response response) { throw unsupported("anime episodes"); }
    protected Request videoListRequest(SEpisode episode) {
        return RequestsKt.GET(getBaseUrl() + episode.getUrl(), getHeaders(), null);
    }
    protected List<Video> videoListParse(Response response) { throw unsupported("anime videos"); }
    protected Request hosterListRequest(SEpisode episode) {
        return RequestsKt.GET(getBaseUrl() + episode.getUrl(), getHeaders(), null);
    }
    protected List<Hoster> hosterListParse(Response response) { throw unsupported("anime hosters"); }
    protected Request videoListRequest(Hoster hoster) {
        return RequestsKt.GET(hoster.getHosterUrl(), getHeaders(), null);
    }
    protected List<Video> videoListParse(Response response, Hoster hoster) {
        throw unsupported("hoster videos");
    }
    public AnimeFilterList getFilterList() { return new AnimeFilterList(); }
    public String getAnimeUrl(SAnime anime) { return animeDetailsRequest(anime).url().toString(); }
    public String getEpisodeUrl(SEpisode episode) { return episode.getUrl(); }
    protected void setUrlWithoutDomain(SAnime anime, String url) {
        anime.setUrl(getUrlWithoutDomain(url));
    }
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
}
