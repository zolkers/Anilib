package fr.vriege.anilib.platform.desktopextensionhost;

import com.sun.net.httpserver.HttpServer;

import fr.vriege.anilib.framework.concurrent.runtime.ManagedExecutors;
import fr.vriege.anilib.platform.desktopextensionhost.compat.android.media.MediaMetadataRetriever;
import fr.vriege.anilib.platform.desktopextensionhost.compat.android.net.Uri;
import fr.vriege.anilib.platform.desktopextensionhost.compat.android.util.LruCache;
import fr.vriege.anilib.platform.desktopextensionhost.compat.aniyomi.animesource.model.Hoster;
import fr.vriege.anilib.platform.desktopextensionhost.compat.aniyomi.animesource.model.Video;
import fr.vriege.anilib.platform.desktopextensionhost.compat.aniyomi.network.OkHttpExtensionsKt;
import fr.vriege.anilib.platform.desktopextensionhost.compat.aniyomi.network.RequestsKt;
import fr.vriege.anilib.platform.desktopextensionhost.compat.aniyomi.network.interceptor.RateLimitInterceptorKt;
import fr.vriege.anilib.platform.desktopextensionhost.compat.aniyomi.util.CoroutinesExtensionsKt;

import kotlin.ResultKt;
import kotlin.coroutines.Continuation;
import kotlin.coroutines.CoroutineContext;
import kotlin.coroutines.EmptyCoroutineContext;
import kotlin.coroutines.intrinsics.IntrinsicsKt;

import kotlinx.coroutines.JobKt;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

final class ModernAnimeCompatibilitySmoke {
    private ModernAnimeCompatibilitySmoke() {}

    static void verify() throws Exception {
        try (var input =
                ModernAnimeCompatibilitySmoke.class.getResourceAsStream("/aniyomi-api-v16.api")) {
            if (input == null)
                throw new IllegalStateException("Official Aniyomi API reference is missing");
            var lines = new String(input.readAllBytes(), StandardCharsets.UTF_8).lines().toList();
            require(lines.size() > 500, "Official API reference is incomplete");
            require(
                    OfficialExtensionApi.inspect(lines).isEmpty(),
                    "Official Aniyomi v16 API differs from the host: "
                            + OfficialExtensionApi.inspect(lines));
        }
        verifyModels();
        verifyNetwork();
        verifyParallelCoroutines();
    }

    private static void verifyModels() throws Exception {
        Video video = new Video();
        try {
            var lookup = MethodHandles.publicLookup();
            var legacyPart =
                    lookup.findVirtual(
                            Video.class, "component3", MethodType.methodType(String.class));
            var modernPart =
                    lookup.findVirtual(
                            Video.class, "component3", MethodType.methodType(Integer.class));
            var rate =
                    lookup.findStatic(
                            RateLimitInterceptorKt.class,
                            "rateLimit-SxA4cEA",
                            MethodType.methodType(
                                    OkHttpClient.Builder.class,
                                    OkHttpClient.Builder.class,
                                    int.class,
                                    long.class));
            Video legacy = new Video("page", "quality", "stream", null, List.of(), List.of());
            require(
                    legacyPart.invoke(legacy).equals("stream") && modernPart.invoke(video) == null,
                    "Legacy/modern JVM return-type bridges differ");
            OkHttpClient.Builder builder = new OkHttpClient.Builder();
            require(
                    rate.invoke(builder, 2, 2_000_000_000L) == builder
                            && builder.interceptors().size() == 1,
                    "Kotlin Duration mangled bridge is not executable");
        } catch (Throwable failure) {
            throw new IllegalStateException("JVM compatibility bridge failed", failure);
        }
        Hoster hoster = new Hoster("u", null, List.of(video), null, false, 2 | 8 | 16, null);
        require(
                hoster.getHosterName().isEmpty() && hoster.getInternalData().isEmpty(),
                "Hoster Kotlin defaults differ");
        require(
                Hoster.Companion.toHosterList(List.of(video))
                        .getFirst()
                        .getVideoList()
                        .equals(List.of(video)),
                "Hoster list lost videos");
        require(
                Hoster.copy$default(hoster, null, "renamed", null, null, true, 1 | 4 | 8, null)
                        .getLazy(),
                "Hoster copy masks differ");
        String text = "a+b c/é日本";
        require(
                Uri.decode(Uri.encode(text)).equals(text) && Uri.decode("a+b").equals("a+b"),
                "Android URI round trip differs");
        require(Uri.encode("a/b c", "/").equals("a/b%20c"), "Android URI allow-list differs");
        require(
                Uri.encode(null) == null && Uri.decode(null) == null,
                "Android URI null behavior differs");
        LruCache<String, String> cache = new LruCache<>(2);
        cache.put("a", "A");
        cache.put("b", "B");
        cache.get("a");
        cache.put("c", "C");
        require(
                cache.snapshot().keySet().equals(Set.of("a", "c")) && cache.evictionCount() == 1,
                "LRU access-order eviction failed");
        cache.resize(1);
        require(cache.size() == 1, "LRU resize failed");
        cache.evictAll();
        require(cache.size() == 0, "LRU clear failed");
        require(
                MediaMetadataRetriever.parseMetadata(
                                "format.duration=\"12.345\"\nstreams.stream.0.width=1920\n")
                        .get(MediaMetadataRetriever.METADATA_KEY_DURATION)
                        .equals("12345"),
                "Media duration is not in milliseconds");
        require(
                ExtensionScriptValues.decode(ExtensionScriptValues.quote(text)).equals(text),
                "Script transport corrupted Unicode");
    }

    private static void verifyNetwork() throws Exception {
        HttpServer server =
                HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch cancellationEntered = new CountDownLatch(1);
        CountDownLatch cancellationRelease = new CountDownLatch(1);
        server.createContext(
                "/cancel",
                exchange -> {
                    cancellationEntered.countDown();
                    try {
                        cancellationRelease.await(3, TimeUnit.SECONDS);
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                    }
                    exchange.close();
                });
        server.createContext(
                "/video",
                exchange -> {
                    entered.countDown();
                    try {
                        release.await(3, TimeUnit.SECONDS);
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                    }
                    byte[] bytes = "stream".getBytes(StandardCharsets.UTF_8);
                    try (exchange) {
                        exchange.sendResponseHeaders(200, bytes.length);
                        exchange.getResponseBody().write(bytes);
                    }
                });
        server.start();
        OkHttpClient client = new OkHttpClient();
        String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/video";
        try {
            Recording<Response> recording = new Recording<>(EmptyCoroutineContext.INSTANCE);
            Object value = RequestsKt.get$default(client, url, null, null, recording, 2 | 4, null);
            require(
                    value == IntrinsicsKt.getCOROUTINE_SUSPENDED(),
                    "Source request blocked instead of suspending");
            require(entered.await(2, TimeUnit.SECONDS), "Source request never reached the server");
            release.countDown();
            try (Response response = recording.result.get(3, TimeUnit.SECONDS)) {
                require(response.body().string().equals("stream"), "Suspend GET lost its response");
                require(
                        response.request().cacheControl().maxAgeSeconds() == 600,
                        "Source request cache default differs from Aniyomi");
            }
            var job = JobKt.Job(null);
            var call =
                    client.newCall(
                            new Request.Builder().url(url.replace("/video", "/cancel")).build());
            Recording<Response> cancelled = new Recording<>(job);
            OkHttpExtensionsKt.await(call, cancelled);
            require(
                    cancellationEntered.await(2, TimeUnit.SECONDS),
                    "Cancellable request did not start");
            job.cancel(new CancellationException("test"));
            require(call.isCanceled(), "Cancelling the coroutine did not cancel HTTP");
        } finally {
            release.countDown();
            cancellationRelease.countDown();
            server.stop(0);
            client.dispatcher().executorService().shutdown();
            client.connectionPool().evictAll();
        }
    }

    private static void verifyParallelCoroutines() {
        List<Integer> result =
                CoroutinesExtensionsKt.parallelMapBlocking(
                        List.of(3, 1, 2),
                        (value, continuation) -> {
                            ManagedExecutors.run(
                                    "parallel-source-test",
                                    () -> continuation.resumeWith(value * 2));
                            return IntrinsicsKt.getCOROUTINE_SUSPENDED();
                        });
        require(
                result.equals(List.of(6, 2, 4)),
                "Parallel coroutine helper lost suspension or ordering");
        List<Integer> flattened =
                CoroutinesExtensionsKt.parallelCatchingFlatMapBlocking(
                        List.of(1, 2, 3),
                        (value, continuation) -> {
                            if (value == 2) throw new IllegalArgumentException("bad hoster");
                            return List.of(value);
                        });
        require(flattened.equals(List.of(1, 3)), "Catching hoster mapping lost healthy results");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    private static final class Recording<T> implements Continuation<T> {
        private final CoroutineContext context;
        private final CompletableFuture<T> result = new CompletableFuture<>();

        private Recording(CoroutineContext context) {
            this.context = context;
        }

        @Override
        public CoroutineContext getContext() {
            return context;
        }

        @Override
        public void resumeWith(Object value) {
            try {
                ResultKt.throwOnFailure(value);
                result.complete(cast(value));
            } catch (Throwable failure) {
                result.completeExceptionally(failure);
            }
        }

        @SuppressWarnings("unchecked")
        private T cast(Object value) {
            return (T) value;
        }
    }
}
