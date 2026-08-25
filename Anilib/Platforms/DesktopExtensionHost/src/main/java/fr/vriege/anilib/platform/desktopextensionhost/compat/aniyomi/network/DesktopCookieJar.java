package fr.vriege.anilib.platform.desktopextensionhost.compat.aniyomi.network;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import okhttp3.Cookie;
import okhttp3.CookieJar;
import okhttp3.HttpUrl;

public final class DesktopCookieJar implements CookieJar {
    private static final DesktopCookieJar INSTANCE = new DesktopCookieJar();
    private final Map<String, Cookie> cookies = new ConcurrentHashMap<>();

    private DesktopCookieJar() {
    }

    public static DesktopCookieJar shared() {
        return INSTANCE;
    }

    @Override
    public void saveFromResponse(HttpUrl url, List<Cookie> values) {
        long now = System.currentTimeMillis();
        for (Cookie cookie : values) {
            String key = key(cookie);
            if (cookie.expiresAt() <= now) {
                cookies.remove(key);
            } else {
                cookies.put(key, cookie);
            }
        }
    }

    @Override
    public List<Cookie> loadForRequest(HttpUrl url) {
        long now = System.currentTimeMillis();
        List<Cookie> result = new ArrayList<>();
        for (Map.Entry<String, Cookie> entry : cookies.entrySet()) {
            Cookie cookie = entry.getValue();
            if (cookie.expiresAt() <= now) {
                cookies.remove(entry.getKey(), cookie);
            } else if (cookie.matches(url)) {
                result.add(cookie);
            }
        }
        return List.copyOf(result);
    }

    public void setCookie(String url, String header) {
        HttpUrl location = HttpUrl.get(url);
        Cookie cookie = Cookie.parse(location, header);
        if (cookie != null) {
            saveFromResponse(location, List.of(cookie));
        }
    }

    public String cookieHeader(String url) {
        List<Cookie> values = loadForRequest(HttpUrl.get(url));
        if (values.isEmpty()) {
            return null;
        }
        return String.join("; ", values.stream().map(cookie -> cookie.name() + '=' + cookie.value()).toList());
    }

    public boolean hasCookies() {
        removeExpired();
        return !cookies.isEmpty();
    }

    public boolean clear() {
        boolean changed = !cookies.isEmpty();
        cookies.clear();
        return changed;
    }

    public boolean removeSessionCookies() {
        int before = cookies.size();
        cookies.entrySet().removeIf(entry -> !entry.getValue().persistent());
        return cookies.size() != before;
    }

    private void removeExpired() {
        long now = System.currentTimeMillis();
        cookies.entrySet().removeIf(entry -> entry.getValue().expiresAt() <= now);
    }

    private static String key(Cookie cookie) {
        return cookie.domain() + '\n' + cookie.path() + '\n' + cookie.name();
    }
}
