package fr.vriege.anilib.platform.desktopextensionhost.compat.android.webkit;

import fr.vriege.anilib.platform.desktopextensionhost.compat.aniyomi.network.DesktopCookieJar;

public final class CookieManager {
    private static final CookieManager INSTANCE = new CookieManager();
    private volatile boolean acceptCookie = true;

    private CookieManager() {
    }

    public static CookieManager getInstance() {
        return INSTANCE;
    }

    public void setAcceptCookie(boolean accept) {
        acceptCookie = accept;
    }

    public boolean acceptCookie() {
        return acceptCookie;
    }

    public void setCookie(String url, String value) {
        if (acceptCookie) {
            DesktopCookieJar.shared().setCookie(url, value);
        }
    }

    public void setCookie(String url, String value, ValueCallback<Boolean> callback) {
        setCookie(url, value);
        if (callback != null) {
            callback.onReceiveValue(acceptCookie);
        }
    }

    public String getCookie(String url) {
        return DesktopCookieJar.shared().cookieHeader(url);
    }

    public boolean hasCookies() {
        return DesktopCookieJar.shared().hasCookies();
    }

    public void removeAllCookie() {
        DesktopCookieJar.shared().clear();
    }

    public void removeAllCookies(ValueCallback<Boolean> callback) {
        boolean removed = DesktopCookieJar.shared().clear();
        if (callback != null) {
            callback.onReceiveValue(removed);
        }
    }

    public void removeSessionCookie() {
        DesktopCookieJar.shared().removeSessionCookies();
    }

    public void removeSessionCookies(ValueCallback<Boolean> callback) {
        boolean removed = DesktopCookieJar.shared().removeSessionCookies();
        if (callback != null) {
            callback.onReceiveValue(removed);
        }
    }

    public void flush() {
        // Cookies are held by the isolated extension-host process and are immediately visible.
    }
}
