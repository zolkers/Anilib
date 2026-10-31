package fr.vriege.anilib.platform.desktopextensionhost.extension;

import com.sun.net.httpserver.HttpServer;

import fr.vriege.anilib.platform.desktopextensionhost.compat.aniyomi.animesource.AnimeCatalogueSource;
import fr.vriege.anilib.platform.desktopextensionhost.compat.aniyomi.animesource.model.AnimeFilterList;
import fr.vriege.anilib.platform.desktopextensionhost.compat.aniyomi.animesource.model.AnimesPage;
import fr.vriege.anilib.platform.desktopextensionhost.compat.aniyomi.animesource.model.Hoster;
import fr.vriege.anilib.platform.desktopextensionhost.compat.aniyomi.animesource.model.SAnime;
import fr.vriege.anilib.platform.desktopextensionhost.compat.aniyomi.animesource.model.SEpisode;
import fr.vriege.anilib.platform.desktopextensionhost.compat.aniyomi.animesource.online.ParsedAnimeHttpSource;

import kotlin.coroutines.Continuation;

import okhttp3.Request;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import rx.Observable;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;

public final class ParsedAnimeSourceSmoke {
    private ParsedAnimeSourceSmoke() {}

    public static void verify() throws Exception {
        HttpServer server =
                HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext(
                "/",
                exchange -> {
                    byte[] html =
                            "<a class='title' href='/one'>One</a>".getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
                    exchange.sendResponseHeaders(200, html.length);
                    try (var body = exchange.getResponseBody()) {
                        body.write(html);
                    }
                });
        server.start();
        try {
            ParsedSource source =
                    new ParsedSource("http://127.0.0.1:" + server.getAddress().getPort());
            ExtensionCompatibility.requireSupported(ExtensionKind.ANIME, source);
            if (!ExtensionOperationDispatcher.hasClassicImplementation(
                            source, "popularAnimeParse", (Object) null)
                    || !ExtensionOperationDispatcher.supportsHosters(source)) {
                throw new IllegalStateException("Parsed source hooks were rejected");
            }
            AnimesPage page = (AnimesPage) source.getPopularAnime(1, null);
            if (!page.getAnimes().getFirst().getTitle().equals("One")) {
                throw new IllegalStateException(
                        "Inherited parsed catalogue did not execute its hooks");
            }
            source.fetchSearchAnime(1, "one", new AnimeFilterList()).toBlocking().single();
            KotlinBridgeSource bridge = new KotlinBridgeSource();
            if (!((AnimesPage) bridge.getPopularAnime(1, null))
                    .getAnimes()
                    .getFirst()
                    .getTitle()
                    .equals("Bridge")) {
                throw new IllegalStateException(
                        "Kotlin DefaultImpls did not invoke the reactive implementation");
            }
        } finally {
            server.stop(0);
        }
    }

    private static final class ParsedSource extends ParsedAnimeHttpSource {
        private final String baseUrl;

        private ParsedSource(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        @Override
        public String getBaseUrl() {
            return baseUrl;
        }

        @Override
        public String getName() {
            return "Parsed fixture";
        }

        @Override
        public String getLang() {
            return "en";
        }

        @Override
        public boolean getSupportsLatest() {
            return false;
        }

        @Override
        protected Request popularAnimeRequest(int page) {
            return new Request.Builder().url(baseUrl).build();
        }

        @Override
        protected Request searchAnimeRequest(int page, String query, AnimeFilterList filters) {
            return popularAnimeRequest(page);
        }

        @Override
        protected String popularAnimeSelector() {
            return "a.title";
        }

        @Override
        protected SAnime popularAnimeFromElement(Element element) {
            SAnime anime = SAnime.Companion.create();
            anime.setTitle(element.text());
            anime.setUrl(element.attr("href"));
            return anime;
        }

        @Override
        protected String searchAnimeSelector() {
            return popularAnimeSelector();
        }

        @Override
        protected SAnime searchAnimeFromElement(Element element) {
            return popularAnimeFromElement(element);
        }

        @Override
        protected SAnime animeDetailsParse(Document document) {
            return SAnime.Companion.create();
        }

        @Override
        protected String episodeListSelector() {
            return "a.title";
        }

        @Override
        protected SEpisode episodeFromElement(Element element) {
            return SEpisode.Companion.create();
        }

        @Override
        protected String hosterListSelector() {
            return "a.title";
        }

        @Override
        protected Hoster hosterFromElement(Element element) {
            return new Hoster();
        }
    }

    private static final class KotlinBridgeSource implements AnimeCatalogueSource {
        @Override
        public long getId() {
            return 1;
        }

        @Override
        public String getName() {
            return "Bridge";
        }

        @Override
        public boolean getSupportsLatest() {
            return false;
        }

        @Override
        public Object getPopularAnime(int page, Continuation<? super AnimesPage> continuation) {
            // This is the forwarding method emitted by Kotlin in compatibility mode.
            return DefaultImpls.getPopularAnime(this, page, continuation);
        }

        @Override
        public Observable<AnimesPage> fetchPopularAnime(int page) {
            SAnime anime = SAnime.Companion.create();
            anime.setTitle("Bridge");
            return Observable.just(new AnimesPage(List.of(anime), false));
        }
    }
}
