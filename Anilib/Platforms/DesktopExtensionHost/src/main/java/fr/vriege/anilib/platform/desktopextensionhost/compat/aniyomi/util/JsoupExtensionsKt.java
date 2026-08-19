package fr.vriege.anilib.platform.desktopextensionhost.compat.aniyomi.util;

import java.io.IOException;
import java.io.UncheckedIOException;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

public final class JsoupExtensionsKt {
    private JsoupExtensionsKt() {
    }

    public static Document asJsoup(Response response, String html) {
        String baseUrl = response.request().url().toString();
        return Jsoup.parse(html == null ? body(response) : html, baseUrl);
    }

    public static Document asJsoup$default(Response response, String html, int mask, Object marker) {
        return asJsoup(response, (mask & 1) == 0 ? html : null);
    }

    private static String body(Response response) {
        ResponseBody body = response.body();
        try {
            return body.string();
        } catch (IOException exception) {
            throw new UncheckedIOException("Unable to read source response", exception);
        }
    }
}
