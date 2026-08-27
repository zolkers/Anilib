package fr.vriege.anilib.platform.desktopextensionhost;

import fr.vriege.anilib.platform.desktopextensionhost.compat.android.content.Context;
import fr.vriege.anilib.platform.desktopextensionhost.compat.android.net.Uri;
import fr.vriege.anilib.platform.desktopextensionhost.compat.android.os.Handler;
import fr.vriege.anilib.platform.desktopextensionhost.compat.android.os.Looper;
import fr.vriege.anilib.platform.desktopextensionhost.compat.android.util.Base64;
import fr.vriege.anilib.platform.desktopextensionhost.compat.android.webkit.CookieManager;
import fr.vriege.anilib.platform.desktopextensionhost.compat.android.webkit.WebView;
import fr.vriege.anilib.platform.desktopextensionhost.compat.android.webkit.WebViewClient;
import fr.vriege.anilib.platform.desktopextensionhost.compat.android.webkit.WebResourceRequest;
import fr.vriege.anilib.platform.desktopextensionhost.compat.android.webkit.WebResourceResponse;
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
import java.util.concurrent.CountDownLatch;
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
        verifyAndroidSchedulingAndWebView();
        verifyCookieBridge();
        verifyParsedSource();
        verifyVideoDataClassBridge();
        verifyRateLimitBuilder();
    }

    private static void verifyAndroidSchedulingAndWebView() {
        Handler handler = new Handler(Looper.getMainLooper());
        CountDownLatch posted = new CountDownLatch(1);
        if (!handler.post(posted::countDown)) {
            throw new IllegalStateException("Android handler rejected its task");
        }
        CountDownLatch delayed = new CountDownLatch(1);
        if (!handler.postDelayed(delayed::countDown, 5L)) {
            throw new IllegalStateException("Android delayed handler rejected its task");
        }
        try {
            if (!posted.await(1L, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Android handler did not execute its task");
            }
            if (!delayed.await(1L, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Android delayed handler did not execute its task");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Android delayed handler smoke was interrupted", exception);
        }
        CountDownLatch intercepted = new CountDownLatch(1);
        WebView view = new WebView(new Context() { });
        view.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView webView, WebResourceRequest request) {
                intercepted.countDown();
                return null;
            }
        });
        view.loadUrl("https://cdn.example/video.mp4");
        try {
            if (!intercepted.await(1L, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Android WebView single-argument load did not dispatch its request");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Android WebView dispatch smoke was interrupted", exception);
        }
        view.destroy();
        if (view.getContext().getMainLooper() != Looper.getMainLooper()) {
            throw new IllegalStateException("Android context does not expose the main looper");
        }
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
        Video modern = new Video(
                "https://resolver.example/video",
                "1080p",
                1080,
                null,
                headers,
                false,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                "resolver-token",
                false);
        Video resolved = Video.copy$default(
                modern,
                null,
                null,
                null,
                null,
                null,
                false,
                List.of(new Track("https://cdn.example/subtitles.vtt", "fr")),
                null,
                null,
                null,
                null,
                null,
                null,
                true,
                1 | 2 | 4 | 8 | 16 | 32 | 128 | 256 | 512 | 1024 | 2048 | 4096,
                null);
        if (!resolved.getInitialized()
                || !"resolver-token".equals(resolved.getInternalData())
                || resolved.getSubtitleTracks().size() != 1) {
            throw new IllegalStateException("Modern Video.copy default bridge failed");
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
