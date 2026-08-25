package fr.vriege.anilib.platform.desktopextensionhost.compat.aniyomi.animesource.online;

import fr.vriege.anilib.platform.desktopextensionhost.compat.aniyomi.animesource.model.AnimesPage;
import fr.vriege.anilib.platform.desktopextensionhost.compat.aniyomi.animesource.model.Hoster;
import fr.vriege.anilib.platform.desktopextensionhost.compat.aniyomi.animesource.model.SAnime;
import fr.vriege.anilib.platform.desktopextensionhost.compat.aniyomi.animesource.model.SEpisode;
import fr.vriege.anilib.platform.desktopextensionhost.compat.aniyomi.animesource.model.Video;
import fr.vriege.anilib.platform.desktopextensionhost.compat.aniyomi.util.JsoupExtensionsKt;
import java.util.List;
import okhttp3.Response;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

public abstract class ParsedAnimeHttpSource extends AnimeHttpSource {
    public ParsedAnimeHttpSource() {
    }

    @Override
    protected AnimesPage popularAnimeParse(Response response) {
        Document document = document(response);
        return new AnimesPage(document.select(popularAnimeSelector()).stream()
                .map(this::popularAnimeFromElement).toList(), hasNext(document, popularAnimeNextPageSelector()));
    }

    protected String popularAnimeSelector() { throw unsupported("popular anime selector"); }
    protected SAnime popularAnimeFromElement(Element element) { throw unsupported("popular anime element"); }
    protected String popularAnimeNextPageSelector() { return null; }

    @Override
    protected AnimesPage searchAnimeParse(Response response) {
        Document document = document(response);
        return new AnimesPage(document.select(searchAnimeSelector()).stream()
                .map(this::searchAnimeFromElement).toList(), hasNext(document, searchAnimeNextPageSelector()));
    }

    protected String searchAnimeSelector() { throw unsupported("search anime selector"); }
    protected SAnime searchAnimeFromElement(Element element) { throw unsupported("search anime element"); }
    protected String searchAnimeNextPageSelector() { return null; }

    @Override
    protected AnimesPage latestUpdatesParse(Response response) {
        Document document = document(response);
        return new AnimesPage(document.select(latestUpdatesSelector()).stream()
                .map(this::latestUpdatesFromElement).toList(), hasNext(document, latestUpdatesNextPageSelector()));
    }

    protected String latestUpdatesSelector() { throw unsupported("latest anime selector"); }
    protected SAnime latestUpdatesFromElement(Element element) { throw unsupported("latest anime element"); }
    protected String latestUpdatesNextPageSelector() { return null; }

    @Override
    protected SAnime animeDetailsParse(Response response) { return animeDetailsParse(document(response)); }
    protected SAnime animeDetailsParse(Document document) { throw unsupported("anime details document"); }

    @Override
    protected List<SEpisode> episodeListParse(Response response) {
        return document(response).select(episodeListSelector()).stream().map(this::episodeFromElement).toList();
    }
    protected String episodeListSelector() { throw unsupported("episode selector"); }
    protected SEpisode episodeFromElement(Element element) { throw unsupported("episode element"); }

    @Override
    protected List<SAnime> seasonListParse(Response response) {
        return document(response).select(seasonListSelector()).stream().map(this::seasonFromElement).toList();
    }
    protected String seasonListSelector() { throw unsupported("season selector"); }
    protected SAnime seasonFromElement(Element element) { throw unsupported("season element"); }

    @Override
    protected List<Hoster> hosterListParse(Response response) {
        return document(response).select(hosterListSelector()).stream().map(this::hosterFromElement).toList();
    }
    protected String hosterListSelector() { throw unsupported("hoster selector"); }
    protected Hoster hosterFromElement(Element element) { throw unsupported("hoster element"); }

    @Override
    protected List<Video> videoListParse(Response response) {
        return document(response).select(videoListSelector()).stream().map(this::videoFromElement).toList();
    }
    protected String videoListSelector() { throw unsupported("video selector"); }
    protected Video videoFromElement(Element element) { throw unsupported("video element"); }

    @Override
    protected String videoUrlParse(Response response) { return videoUrlParse(document(response)); }
    protected String videoUrlParse(Document document) { throw unsupported("video URL document"); }

    private static Document document(Response response) {
        return JsoupExtensionsKt.asJsoup$default(response, null, 1, null);
    }

    private static boolean hasNext(Document document, String selector) {
        return selector != null && document.selectFirst(selector) != null;
    }

    private static UnsupportedOperationException unsupported(String operation) {
        return new UnsupportedOperationException("Source does not implement " + operation);
    }
}
