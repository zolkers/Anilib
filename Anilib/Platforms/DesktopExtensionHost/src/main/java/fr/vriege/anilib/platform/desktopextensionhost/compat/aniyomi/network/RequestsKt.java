package fr.vriege.anilib.platform.desktopextensionhost.compat.aniyomi.network;

import okhttp3.CacheControl;
import okhttp3.Headers;
import okhttp3.HttpUrl;
import okhttp3.Request;
import okhttp3.RequestBody;

public final class RequestsKt {
    private RequestsKt() {
    }

    public static Request GET(String url, Headers headers, CacheControl cacheControl) {
        return request(new Request.Builder().url(url), headers, cacheControl).get().build();
    }

    public static Request GET(HttpUrl url, Headers headers, CacheControl cacheControl) {
        return request(new Request.Builder().url(url), headers, cacheControl).get().build();
    }

    public static Request GET$default(
            String url, Headers headers, CacheControl cacheControl, int mask, Object marker) {
        Headers selectedHeaders = (mask & 2) == 0 ? headers : new Headers.Builder().build();
        CacheControl selectedCache = (mask & 4) == 0 ? cacheControl : null;
        return GET(url, selectedHeaders, selectedCache);
    }

    public static Request GET$default(
            HttpUrl url, Headers headers, CacheControl cacheControl, int mask, Object marker) {
        Headers selectedHeaders = (mask & 2) == 0 ? headers : new Headers.Builder().build();
        CacheControl selectedCache = (mask & 4) == 0 ? cacheControl : null;
        return GET(url, selectedHeaders, selectedCache);
    }

    public static Request POST(String url, Headers headers, RequestBody body, CacheControl cacheControl) {
        return request(new Request.Builder().url(url), headers, cacheControl).post(body).build();
    }

    public static Request POST$default(
            String url,
            Headers headers,
            RequestBody body,
            CacheControl cacheControl,
            int mask,
            Object marker) {
        Headers selectedHeaders = (mask & 2) == 0 ? headers : new Headers.Builder().build();
        CacheControl selectedCache = (mask & 8) == 0 ? cacheControl : null;
        return POST(url, selectedHeaders, body, selectedCache);
    }

    private static Request.Builder request(
            Request.Builder builder, Headers headers, CacheControl cacheControl) {
        if (headers != null) {
            builder.headers(headers);
        }
        if (cacheControl != null) {
            builder.cacheControl(cacheControl);
        }
        return builder;
    }
}
