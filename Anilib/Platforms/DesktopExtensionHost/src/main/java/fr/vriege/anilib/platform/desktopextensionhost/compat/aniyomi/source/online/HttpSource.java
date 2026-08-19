package fr.vriege.anilib.platform.desktopextensionhost.compat.aniyomi.source.online;

import fr.vriege.anilib.platform.desktopextensionhost.compat.aniyomi.source.CatalogueSource;
import fr.vriege.anilib.platform.desktopextensionhost.compat.aniyomi.network.NetworkHelper;
import fr.vriege.anilib.platform.desktopextensionhost.compat.aniyomi.network.OkHttpExtensionsKt;
import fr.vriege.anilib.platform.desktopextensionhost.compat.aniyomi.network.RequestsKt;
import fr.vriege.anilib.platform.desktopextensionhost.compat.aniyomi.source.model.FilterList;
import fr.vriege.anilib.platform.desktopextensionhost.compat.aniyomi.source.model.MangasPage;
import fr.vriege.anilib.platform.desktopextensionhost.compat.aniyomi.source.model.Page;
import fr.vriege.anilib.platform.desktopextensionhost.compat.aniyomi.source.model.SChapter;
import fr.vriege.anilib.platform.desktopextensionhost.compat.aniyomi.source.model.SManga;
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

public abstract class HttpSource implements CatalogueSource {
    private final NetworkHelper network = NetworkHelper.shared();
    private Long id;
    private Headers headers;

    public HttpSource() {
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

    protected Request popularMangaRequest(int page) { throw unsupported("popular manga"); }
    protected MangasPage popularMangaParse(Response response) { throw unsupported("popular manga"); }
    protected Request searchMangaRequest(int page, String query, FilterList filters) {
        throw unsupported("manga search");
    }
    protected MangasPage searchMangaParse(Response response) { throw unsupported("manga search"); }
    protected Request latestUpdatesRequest(int page) { throw unsupported("latest manga"); }
    protected MangasPage latestUpdatesParse(Response response) { throw unsupported("latest manga"); }

    public Request mangaDetailsRequest(SManga manga) {
        return RequestsKt.GET(getBaseUrl() + manga.getUrl(), getHeaders(), null);
    }
    protected SManga mangaDetailsParse(Response response) { throw unsupported("manga details"); }
    protected Request chapterListRequest(SManga manga) {
        return RequestsKt.GET(getBaseUrl() + manga.getUrl(), getHeaders(), null);
    }
    protected List<SChapter> chapterListParse(Response response) { throw unsupported("manga chapters"); }
    protected Request pageListRequest(SChapter chapter) {
        return RequestsKt.GET(getBaseUrl() + chapter.getUrl(), getHeaders(), null);
    }
    protected List<Page> pageListParse(Response response) { throw unsupported("manga pages"); }
    protected Request imageUrlRequest(Page page) {
        return RequestsKt.GET(page.getUrl(), getHeaders(), null);
    }
    protected String imageUrlParse(Response response) { throw unsupported("manga image URL"); }
    protected Request imageRequest(Page page) {
        String imageUrl = page.getImageUrl();
        if (imageUrl == null || imageUrl.isBlank()) {
            throw new IllegalStateException("Manga page image URL has not been resolved");
        }
        return RequestsKt.GET(imageUrl, getHeaders(), null);
    }
    public FilterList getFilterList() { return new FilterList(); }

    public Observable<MangasPage> fetchPopularManga(int page) {
        return OkHttpExtensionsKt.asObservableSuccess(getClient().newCall(popularMangaRequest(page)))
                .map(response -> {
                    try (response) {
                        return popularMangaParse(response);
                    }
                });
    }

    public Observable<MangasPage> fetchSearchManga(int page, String query, FilterList filters) {
        return OkHttpExtensionsKt.asObservableSuccess(
                getClient().newCall(searchMangaRequest(page, query, filters)))
                .map(response -> {
                    try (response) {
                        return searchMangaParse(response);
                    }
                });
    }

    public Observable<MangasPage> fetchLatestUpdates(int page) {
        return OkHttpExtensionsKt.asObservableSuccess(getClient().newCall(latestUpdatesRequest(page)))
                .map(response -> {
                    try (response) {
                        return latestUpdatesParse(response);
                    }
                });
    }

    public Observable<SManga> fetchMangaDetails(SManga manga) {
        return OkHttpExtensionsKt.asObservableSuccess(getClient().newCall(mangaDetailsRequest(manga)))
                .map(response -> {
                    try (response) {
                        return mangaDetailsParse(response);
                    }
                });
    }

    public Observable<List<SChapter>> fetchChapterList(SManga manga) {
        return OkHttpExtensionsKt.asObservableSuccess(getClient().newCall(chapterListRequest(manga)))
                .map(response -> {
                    try (response) {
                        return chapterListParse(response);
                    }
                });
    }

    public Observable<List<Page>> fetchPageList(SChapter chapter) {
        return OkHttpExtensionsKt.asObservableSuccess(getClient().newCall(pageListRequest(chapter)))
                .map(response -> {
                    try (response) {
                        return pageListParse(response);
                    }
                });
    }

    public Observable<String> fetchImageUrl(Page page) {
        return OkHttpExtensionsKt.asObservableSuccess(getClient().newCall(imageUrlRequest(page)))
                .map(response -> {
                    try (response) {
                        return imageUrlParse(response);
                    }
                });
    }

    public String getMangaUrl(SManga manga) { return mangaDetailsRequest(manga).url().toString(); }
    public String getChapterUrl(SChapter chapter) { return pageListRequest(chapter).url().toString(); }
    protected void setUrlWithoutDomain(SManga manga, String url) {
        manga.setUrl(getUrlWithoutDomain(url));
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
