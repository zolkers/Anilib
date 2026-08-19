package fr.vriege.anilib.platform.desktopextensionhost.compat.aniyomi.network;

import okhttp3.CookieJar;
import okhttp3.OkHttpClient;

public final class NetworkHelper {
    private static final String USER_AGENT = "Mozilla/5.0 (Anilib; desktop source host)";
    private static final NetworkHelper SHARED = new NetworkHelper();
    private final CookieJar cookieJar;
    private final OkHttpClient client;

    public NetworkHelper() {
        cookieJar = CookieJar.NO_COOKIES;
        client = new OkHttpClient.Builder().cookieJar(cookieJar).build();
    }

    public static NetworkHelper shared() { return SHARED; }
    public CookieJar getCookieJar() { return cookieJar; }
    public OkHttpClient getClient() { return client; }
    public String defaultUserAgentProvider() { return USER_AGENT; }
}
