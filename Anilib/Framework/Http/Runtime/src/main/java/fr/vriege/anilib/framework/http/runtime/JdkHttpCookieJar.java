package fr.vriege.anilib.framework.http.runtime;

import fr.vriege.anilib.framework.http.HttpCookieJar;
import fr.vriege.anilib.framework.http.HttpException;

import java.io.IOException;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class JdkHttpCookieJar implements HttpCookieJar {
    private final CookieManager manager = new CookieManager(null, CookiePolicy.ACCEPT_ORIGINAL_SERVER);

    public JdkHttpCookieJar() {
    }

    @Override
    public synchronized Map<String, List<String>> requestHeaders(URI uri) {
        Objects.requireNonNull(uri, "uri must not be null");
        try {
            return immutableHeaders(manager.get(uri, Map.of()));
        } catch (IOException exception) {
            throw new HttpException("Unable to read cookies for " + uri.getHost(), exception);
        }
    }

    @Override
    public synchronized void store(URI uri, Map<String, List<String>> responseHeaders) {
        Objects.requireNonNull(uri, "uri must not be null");
        Objects.requireNonNull(responseHeaders, "responseHeaders must not be null");
        try {
            manager.put(uri, responseHeaders);
        } catch (IOException exception) {
            throw new HttpException("Unable to store cookies for " + uri.getHost(), exception);
        }
    }

    @Override
    public synchronized void clear() {
        manager.getCookieStore().removeAll();
    }

    private static Map<String, List<String>> immutableHeaders(Map<String, List<String>> headers) {
        Map<String, List<String>> copy = new LinkedHashMap<>();
        headers.forEach((name, values) -> {
            if (!values.isEmpty()) {
                copy.put(name, List.copyOf(values));
            }
        });
        return Map.copyOf(copy);
    }
}
