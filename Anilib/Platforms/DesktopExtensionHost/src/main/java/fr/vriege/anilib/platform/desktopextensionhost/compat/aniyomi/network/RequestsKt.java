package fr.vriege.anilib.platform.desktopextensionhost.compat.aniyomi.network;

import kotlin.coroutines.Continuation;

import okhttp3.CacheControl;
import okhttp3.FormBody;
import okhttp3.Headers;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.util.concurrent.TimeUnit;

public final class RequestsKt {
    private static final Headers DEFAULT_HEADERS = new Headers.Builder().build();
    private static final CacheControl DEFAULT_CACHE =
            new CacheControl.Builder().maxAge(10, TimeUnit.MINUTES).build();

    private RequestsKt() {}

    public static Request GET(String url, Headers headers, CacheControl cacheControl) {
        return request(new Request.Builder().url(url), headers, cacheControl).get().build();
    }

    public static Request GET(HttpUrl url, Headers headers, CacheControl cacheControl) {
        return request(new Request.Builder().url(url), headers, cacheControl).get().build();
    }

    public static Request GET$default(
            String url, Headers headers, CacheControl cacheControl, int mask, Object marker) {
        Headers selectedHeaders = (mask & 2) == 0 ? headers : DEFAULT_HEADERS;
        CacheControl selectedCache = (mask & 4) == 0 ? cacheControl : DEFAULT_CACHE;
        return GET(url, selectedHeaders, selectedCache);
    }

    public static Request GET$default(
            HttpUrl url, Headers headers, CacheControl cacheControl, int mask, Object marker) {
        Headers selectedHeaders = (mask & 2) == 0 ? headers : DEFAULT_HEADERS;
        CacheControl selectedCache = (mask & 4) == 0 ? cacheControl : DEFAULT_CACHE;
        return GET(url, selectedHeaders, selectedCache);
    }

    public static Request POST(
            String url, Headers headers, RequestBody body, CacheControl cacheControl) {
        return request(new Request.Builder().url(url), headers, cacheControl).post(body).build();
    }

    public static Request POST$default(
            String url,
            Headers headers,
            RequestBody body,
            CacheControl cacheControl,
            int mask,
            Object marker) {
        Headers selectedHeaders = (mask & 2) == 0 ? headers : DEFAULT_HEADERS;
        CacheControl selectedCache = (mask & 8) == 0 ? cacheControl : DEFAULT_CACHE;
        return POST(
                url,
                selectedHeaders,
                (mask & 4) == 0 ? body : new FormBody.Builder().build(),
                selectedCache);
    }

    public static Object get(
            OkHttpClient client,
            String url,
            Headers headers,
            CacheControl cache,
            Continuation<? super Response> continuation) {
        return OkHttpExtensionsKt.awaitSuccess(
                client.newCall(GET(url, headers, cache)), continuation);
    }

    public static Object get(
            OkHttpClient client,
            HttpUrl url,
            Headers headers,
            CacheControl cache,
            Continuation<? super Response> continuation) {
        return OkHttpExtensionsKt.awaitSuccess(
                client.newCall(GET(url, headers, cache)), continuation);
    }

    public static Object get$default(
            OkHttpClient client,
            String url,
            Headers headers,
            CacheControl cache,
            Continuation<? super Response> continuation,
            int mask,
            Object marker) {
        return get(
                client,
                url,
                (mask & 2) == 0 ? headers : DEFAULT_HEADERS,
                (mask & 4) == 0 ? cache : DEFAULT_CACHE,
                continuation);
    }

    public static Object get$default(
            OkHttpClient client,
            HttpUrl url,
            Headers headers,
            CacheControl cache,
            Continuation<? super Response> continuation,
            int mask,
            Object marker) {
        return get(
                client,
                url,
                (mask & 2) == 0 ? headers : DEFAULT_HEADERS,
                (mask & 4) == 0 ? cache : DEFAULT_CACHE,
                continuation);
    }

    public static Object post(
            OkHttpClient client,
            String url,
            Headers headers,
            RequestBody body,
            CacheControl cache,
            Continuation<? super Response> continuation) {
        return OkHttpExtensionsKt.awaitSuccess(
                client.newCall(POST(url, headers, body, cache)), continuation);
    }

    public static Object post$default(
            OkHttpClient client,
            String url,
            Headers headers,
            RequestBody body,
            CacheControl cache,
            Continuation<? super Response> continuation,
            int mask,
            Object marker) {
        return post(
                client,
                url,
                (mask & 2) == 0 ? headers : DEFAULT_HEADERS,
                (mask & 4) == 0 ? body : new FormBody.Builder().build(),
                (mask & 8) == 0 ? cache : DEFAULT_CACHE,
                continuation);
    }

    public static Request PUT(String url, Headers headers, RequestBody body, CacheControl cache) {
        return request(new Request.Builder().url(url), headers, cache).put(body).build();
    }

    public static Request PUT$default(
            String url,
            Headers headers,
            RequestBody body,
            CacheControl cache,
            int mask,
            Object marker) {
        return PUT(
                url,
                (mask & 2) == 0 ? headers : DEFAULT_HEADERS,
                (mask & 4) == 0 ? body : new FormBody.Builder().build(),
                (mask & 8) == 0 ? cache : DEFAULT_CACHE);
    }

    public static Request DELETE(
            String url, Headers headers, RequestBody body, CacheControl cache) {
        return request(new Request.Builder().url(url), headers, cache).delete(body).build();
    }

    public static Request DELETE$default(
            String url,
            Headers headers,
            RequestBody body,
            CacheControl cache,
            int mask,
            Object marker) {
        return DELETE(
                url,
                (mask & 2) == 0 ? headers : DEFAULT_HEADERS,
                (mask & 4) == 0 ? body : new FormBody.Builder().build(),
                (mask & 8) == 0 ? cache : DEFAULT_CACHE);
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
