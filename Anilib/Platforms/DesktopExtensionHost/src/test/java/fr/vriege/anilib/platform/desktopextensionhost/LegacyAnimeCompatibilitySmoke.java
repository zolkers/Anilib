package fr.vriege.anilib.platform.desktopextensionhost;

import fr.vriege.anilib.platform.desktopextensionhost.compat.android.net.Uri;
import fr.vriege.anilib.platform.desktopextensionhost.compat.android.util.Base64;
import fr.vriege.anilib.platform.desktopextensionhost.compat.android.webkit.CookieManager;
import fr.vriege.anilib.platform.desktopextensionhost.compat.aniyomi.animesource.model.AnimesPage;
import fr.vriege.anilib.platform.desktopextensionhost.compat.aniyomi.animesource.model.SAnime;
import fr.vriege.anilib.platform.desktopextensionhost.compat.aniyomi.animesource.model.Track;
import fr.vriege.anilib.platform.desktopextensionhost.compat.aniyomi.animesource.model.Video;
import fr.vriege.anilib.platform.desktopextensionhost.compat.aniyomi.animesource.online.ParsedAnimeHttpSource;
import fr.vriege.anilib.platform.desktopextensionhost.compat.aniyomi.network.NetworkHelper;
import fr.vriege.anilib.platform.desktopextensionhost.compat.aniyomi.network.interceptor.RateLimitInterceptorKt;
import fr.vriege.anilib.platform.desktopextensionhost.compat.aniyomi.network.interceptor
        .SpecificHostRateLimitInterceptorKt;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import okhttp3.Headers;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

public final class LegacyAnimeCompatibilitySmoke {
    private LegacyAnimeCompatibilitySmoke() {
    }

    public static void verify() {
        verifyAndroidUtilities();
        verifyCookieBridge();
        verifyParsedSource();
        verifyVideoDataClassBridge();
        verifyRateLimitBuilder();
    }

    private static void verifyAndroidUtilities() {
        byte[] source = "Anilib+/".getBytes(StandardCharsets.UTF_8);
        String encoded = Base64.encodeToString(source, Base64.NO_WRAP);
        if (!Arrays.equals(source, Base64.decode(encoded, Base64.DEFAULT))) {
            throw new IllegalStateException("Android Base64 compatibility failed");
        }
        if (!Uri.fromFile(new File("extension cache")).toString().startsWith("file:")) {
            throw new IllegalStateException("Android file URI compatibility failed");
        }
    }

    private static void verifyCookieBridge() {
        CookieManager cookies = CookieManager.getInstance();
        cookies.removeAllCookie();
        cookies.setCookie("https://anime.example/path", "session=ready; Path=/; Secure");
        if (!"session=ready".equals(cookies.getCookie("https://anime.example/episode"))) {
            throw new IllegalStateException("WebView cookie compatibility failed");
        }
        if (NetworkHelper.shared().getClient().cookieJar()
                .loadForRequest(HttpUrl.get("https://anime.example/episode")).isEmpty()) {
            throw new IllegalStateException("WebView and OkHttp cookie stores are disconnected");
        }
        cookies.removeAllCookie();
    }

    private static void verifyParsedSource() {
        ParsedFixture source = new ParsedFixture();
        try (Response response = response("<article data-next='1'><a href='/a'>Alpha</a></article>")) {
            var page = source.parse(response);
            if (page.getAnimes().size() != 1 || !page.getHasNextPage()
                    || !"Alpha".equals(page.getAnimes().getFirst().getTitle())) {
                throw new IllegalStateException("Parsed AniYomi source compatibility failed");
            }
        }
    }

    private static void verifyVideoDataClassBridge() {
        Headers headers = new Headers.Builder().add("Referer", "https://anime.example").build();
        Video original = new Video("/watch", "1080p", "https://cdn.example/video.m3u8", headers,
                List.of(), List.of());
        Video copy = Video.copy$default(original, null, "720p", null, null, null, null,
                1 | 4 | 8 | 16 | 32, null);
        if (!"/watch".equals(copy.getUrl()) || !"720p".equals(copy.getQuality())
                || !original.getVideoUrl().equals(copy.getVideoUrl())) {
            throw new IllegalStateException("Legacy Video.copy default bridge failed");
        }
    }

    private static void verifyRateLimitBuilder() {
        OkHttpClient.Builder builder = SpecificHostRateLimitInterceptorKt.rateLimitHost(
                new OkHttpClient.Builder(), HttpUrl.get("https://anime.example"),
                2, 1, TimeUnit.SECONDS);
        if (builder.interceptors().stream().noneMatch(interceptor ->
                interceptor.getClass().getSimpleName().equals("SpecificHostRateLimitInterceptor"))) {
            throw new IllegalStateException("Specific-host rate-limit compatibility failed");
        }
        OkHttpClient.Builder generic = RateLimitInterceptorKt.rateLimit$default(
                new OkHttpClient.Builder(), 3, 0L, null, 2 | 4, null);
        if (generic.interceptors().stream().noneMatch(interceptor ->
                interceptor.getClass().getSimpleName().equals("RateLimitInterceptor"))) {
            throw new IllegalStateException("Generic rate-limit compatibility failed");
        }
    }

    private static Response response(String html) {
        Request request = new Request.Builder().url("https://anime.example/catalog").build();
        return new Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(ResponseBody.create(html, MediaType.get("text/html; charset=utf-8")))
                .build();
    }

    private static final class ParsedFixture extends ParsedAnimeHttpSource {
        @Override public String getName() { return "Fixture"; }
        @Override public String getLang() { return "en"; }
        @Override public boolean getSupportsLatest() { return true; }
        @Override public String getBaseUrl() { return "https://anime.example"; }
        @Override protected String popularAnimeSelector() { return "article"; }
        @Override protected String popularAnimeNextPageSelector() { return "article[data-next]"; }
        @Override protected SAnime popularAnimeFromElement(Element element) {
            SAnime anime = SAnime.Companion.create();
            anime.setTitle(element.text());
            anime.setUrl(element.selectFirst("a").attr("href"));
            return anime;
        }
        @Override protected SAnime animeDetailsParse(Document document) {
            throw new UnsupportedOperationException();
        }
        private AnimesPage parse(Response response) {
            return popularAnimeParse(response);
        }
    }
}
