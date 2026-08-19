package fr.vriege.anilib.platform.desktopextensionhost.extension;

import fr.vriege.anilib.platform.desktopextensionhost.compat.aniyomi.animesource.model.AnimesPage;
import fr.vriege.anilib.platform.desktopextensionhost.compat.aniyomi.animesource.model.SAnime;
import fr.vriege.anilib.platform.desktopextensionhost.compat.aniyomi.animesource.model.SEpisode;
import fr.vriege.anilib.platform.desktopextensionhost.compat.aniyomi.animesource.model.Video;
import fr.vriege.anilib.platform.desktopextensionhost.compat.aniyomi.source.model.MangasPage;
import fr.vriege.anilib.platform.desktopextensionhost.compat.aniyomi.source.model.Page;
import fr.vriege.anilib.platform.desktopextensionhost.compat.aniyomi.source.model.SChapter;
import fr.vriege.anilib.platform.desktopextensionhost.compat.aniyomi.source.model.SManga;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URI;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import kotlin.ResultKt;
import kotlin.coroutines.Continuation;
import kotlin.coroutines.CoroutineContext;
import kotlin.coroutines.EmptyCoroutineContext;
import kotlin.coroutines.intrinsics.IntrinsicsKt;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public final class ExtensionSourceOperations {
    private static final Duration SUSPEND_TIMEOUT = Duration.ofSeconds(45);
    private static final int MAX_PROXY_BYTES = 64 * 1024 * 1024;
    private final ExtensionRuntimeCatalog catalog;

    public ExtensionSourceOperations(ExtensionRuntimeCatalog catalog) {
        this.catalog = Objects.requireNonNull(catalog, "catalog");
    }

    public MangasPage mangaCatalogue(long sourceId, String operation, int page, String query) {
        return withSource(sourceId, ExtensionKind.MANGA, source -> {
            String requestName = switch (operation) {
                case "popular" -> "popularMangaRequest";
                case "latest" -> "latestUpdatesRequest";
                case "search" -> "searchMangaRequest";
                default -> throw new IllegalArgumentException("Unknown manga catalogue operation");
            };
            String parseName = switch (operation) {
                case "popular" -> "popularMangaParse";
                case "latest" -> "latestUpdatesParse";
                case "search" -> "searchMangaParse";
                default -> throw new IllegalArgumentException("Unknown manga catalogue operation");
            };
            Object[] arguments = operation.equals("search")
                    ? new Object[]{page, Objects.requireNonNullElse(query, ""), invoke(source, "getFilterList")}
                    : new Object[]{page};
            return requestAndParse(source, requestName, arguments, parseName, MangasPage.class);
        });
    }

    public AnimesPage animeCatalogue(long sourceId, String operation, int page, String query) {
        return withSource(sourceId, ExtensionKind.ANIME, source -> {
            String requestName = switch (operation) {
                case "popular" -> "popularAnimeRequest";
                case "latest" -> "latestUpdatesRequest";
                case "search" -> "searchAnimeRequest";
                default -> throw new IllegalArgumentException("Unknown anime catalogue operation");
            };
            String parseName = switch (operation) {
                case "popular" -> "popularAnimeParse";
                case "latest" -> "latestUpdatesParse";
                case "search" -> "searchAnimeParse";
                default -> throw new IllegalArgumentException("Unknown anime catalogue operation");
            };
            Object[] arguments = operation.equals("search")
                    ? new Object[]{page, Objects.requireNonNullElse(query, ""), invoke(source, "getFilterList")}
                    : new Object[]{page};
            return requestAndParse(source, requestName, arguments, parseName, AnimesPage.class);
        });
    }

    public SManga mangaDetails(long sourceId, String url) {
        return withSource(sourceId, ExtensionKind.MANGA, source -> {
            SManga manga = SManga.Companion.create();
            manga.setUrl(url);
            SManga result = requestAndParse(
                    source, "mangaDetailsRequest", new Object[]{manga}, "mangaDetailsParse", SManga.class);
            result.setInitialized(true);
            return result;
        });
    }

    public List<SChapter> chapters(long sourceId, String url) {
        return withSource(sourceId, ExtensionKind.MANGA, source -> {
            SManga manga = SManga.Companion.create();
            manga.setUrl(url);
            return listResult(requestAndParse(
                    source, "chapterListRequest", new Object[]{manga}, "chapterListParse", List.class),
                    SChapter.class);
        });
    }

    public List<Page> pages(long sourceId, String url) {
        return withSource(sourceId, ExtensionKind.MANGA, source -> {
            SChapter chapter = SChapter.Companion.create();
            chapter.setUrl(url);
            return listResult(requestAndParse(
                    source, "pageListRequest", new Object[]{chapter}, "pageListParse", List.class), Page.class);
        });
    }

    public SAnime animeDetails(long sourceId, String url) {
        return withSource(sourceId, ExtensionKind.ANIME, source -> {
            SAnime anime = SAnime.Companion.create();
            anime.setUrl(url);
            SAnime result = requestAndParse(
                    source, "animeDetailsRequest", new Object[]{anime}, "animeDetailsParse", SAnime.class);
            result.setInitialized(true);
            return result;
        });
    }

    public List<SEpisode> episodes(long sourceId, String url) {
        return withSource(sourceId, ExtensionKind.ANIME, source -> {
            SAnime anime = SAnime.Companion.create();
            anime.setUrl(url);
            return listResult(requestAndParse(
                    source, "episodeListRequest", new Object[]{anime}, "episodeListParse", List.class),
                    SEpisode.class);
        });
    }

    public List<Video> videos(long sourceId, String url) {
        return withSource(sourceId, ExtensionKind.ANIME, source -> {
            SEpisode episode = SEpisode.Companion.create();
            episode.setUrl(url);
            Object result = invokeSuspend(source, "getVideoList", episode);
            return listResult(result, Video.class);
        });
    }

    public ProxiedResource proxy(long sourceId, String url) {
        URI location = URI.create(url);
        if (!("http".equalsIgnoreCase(location.getScheme()) || "https".equalsIgnoreCase(location.getScheme()))
                || location.getHost() == null) {
            throw new IllegalArgumentException("Proxy URL must be absolute HTTP(S)");
        }
        return withAnySource(sourceId, source -> {
            OkHttpClient client = result(invoke(source, "getClient"), OkHttpClient.class);
            okhttp3.Headers headers = result(invoke(source, "getHeaders"), okhttp3.Headers.class);
            Request request = new Request.Builder().url(location.toString()).headers(headers).build();
            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    throw new IllegalStateException("Source resource failed with HTTP " + response.code());
                }
                okhttp3.ResponseBody body = Objects.requireNonNull(response.body(), "Source resource has no body");
                long length = body.contentLength();
                if (length > MAX_PROXY_BYTES) {
                    throw new IllegalStateException("Source resource exceeds the proxy size limit");
                }
                try (java.io.InputStream input = body.byteStream()) {
                    byte[] bytes = input.readNBytes(MAX_PROXY_BYTES + 1);
                    if (bytes.length > MAX_PROXY_BYTES) {
                        throw new IllegalStateException("Source resource exceeds the proxy size limit");
                    }
                    String contentType = body.contentType() == null
                            ? "application/octet-stream" : body.contentType().toString();
                    return new ProxiedResource(bytes, contentType);
                }
            } catch (java.io.IOException exception) {
                throw new java.io.UncheckedIOException("Source resource request failed", exception);
            }
        });
    }

    private <T> T withSource(long sourceId, ExtensionKind kind, SourceOperation<T> operation) {
        try (ExtensionRuntimeCatalog.Snapshot snapshot = catalog.discover()) {
            LoadedSource loaded = snapshot.sources().stream()
                    .filter(source -> source.id() == sourceId && source.kind() == kind)
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Source is not installed"));
            return operation.apply(loaded.instance());
        }
    }

    private <T> T withAnySource(long sourceId, SourceOperation<T> operation) {
        try (ExtensionRuntimeCatalog.Snapshot snapshot = catalog.discover()) {
            LoadedSource loaded = snapshot.sources().stream()
                    .filter(source -> source.id() == sourceId)
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Source is not installed"));
            return operation.apply(loaded.instance());
        }
    }

    private static <T> T requestAndParse(
            Object source,
            String requestMethod,
            Object[] requestArguments,
            String parseMethod,
            Class<T> resultType) {
        Request request = result(invoke(source, requestMethod, requestArguments), Request.class);
        OkHttpClient client = result(invoke(source, "getClient"), OkHttpClient.class);
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IllegalStateException("Source request failed with HTTP " + response.code());
            }
            return result(invoke(source, parseMethod, response), resultType);
        } catch (java.io.IOException exception) {
            throw new java.io.UncheckedIOException("Source request failed", exception);
        }
    }

    private static Object invokeSuspend(Object source, String name, Object... arguments) {
        CompletableFuture<Object> resumed = new CompletableFuture<>();
        Continuation<Object> continuation = new Continuation<>() {
            @Override public CoroutineContext getContext() { return EmptyCoroutineContext.INSTANCE; }
            @Override public void resumeWith(Object value) {
                try {
                    ResultKt.throwOnFailure(value);
                    resumed.complete(value);
                } catch (RuntimeException | Error failure) {
                    resumed.completeExceptionally(failure);
                }
            }
        };
        Object[] invocation = Arrays.copyOf(arguments, arguments.length + 1);
        invocation[arguments.length] = continuation;
        Object result = invoke(source, name, invocation);
        if (result != IntrinsicsKt.getCOROUTINE_SUSPENDED()) {
            return result;
        }
        try {
            return resumed.get(SUSPEND_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException exception) {
            throw new IllegalStateException("Source operation timed out", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Source operation was interrupted", exception);
        } catch (java.util.concurrent.ExecutionException exception) {
            throw propagate(exception.getCause());
        }
    }

    private static Object invoke(Object target, String name, Object... arguments) {
        Method method = method(target.getClass(), name, arguments);
        try {
            method.setAccessible(true);
            return method.invoke(target, arguments);
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException("Source operation is inaccessible: " + name, exception);
        } catch (InvocationTargetException exception) {
            throw propagate(exception.getCause());
        }
    }

    private static Method method(Class<?> type, String name, Object[] arguments) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            for (Method method : current.getDeclaredMethods()) {
                if (method.getName().equals(name) && compatible(method.getParameterTypes(), arguments)) {
                    return method;
                }
            }
        }
        throw new IllegalStateException("Source operation is unavailable: " + name);
    }

    private static boolean compatible(Class<?>[] parameters, Object[] arguments) {
        if (parameters.length != arguments.length) {
            return false;
        }
        for (int index = 0; index < parameters.length; index++) {
            Object argument = arguments[index];
            Class<?> parameter = parameters[index];
            if (argument == null) {
                if (parameter.isPrimitive()) {
                    return false;
                }
            } else if (!boxed(parameter).isInstance(argument)) {
                return false;
            }
        }
        return true;
    }

    private static Class<?> boxed(Class<?> value) {
        if (!value.isPrimitive()) {
            return value;
        }
        if (value == int.class) return Integer.class;
        if (value == long.class) return Long.class;
        if (value == boolean.class) return Boolean.class;
        if (value == float.class) return Float.class;
        if (value == double.class) return Double.class;
        if (value == short.class) return Short.class;
        if (value == byte.class) return Byte.class;
        if (value == char.class) return Character.class;
        return Void.class;
    }

    private static <T> T result(Object value, Class<T> type) {
        if (type.isInstance(value)) {
            return type.cast(value);
        }
        throw new IllegalStateException("Source operation returned "
                + (value == null ? "null" : value.getClass().getName()) + " instead of " + type.getName());
    }

    private static <T> List<T> listResult(Object value, Class<T> elementType) {
        if (!(value instanceof List<?> values)) {
            throw new IllegalStateException("Source operation did not return a list");
        }
        return values.stream().map(item -> result(item, elementType)).toList();
    }

    private static RuntimeException propagate(Throwable failure) {
        if (failure instanceof RuntimeException runtime) {
            return runtime;
        }
        if (failure instanceof Error error) {
            throw error;
        }
        return new IllegalStateException("Source operation failed", failure);
    }

    @FunctionalInterface
    private interface SourceOperation<T> {
        T apply(Object source);
    }

    public record ProxiedResource(byte[] body, String contentType) {
        public ProxiedResource {
            body = body.clone();
            contentType = Objects.requireNonNull(contentType, "contentType");
        }

        @Override
        public byte[] body() {
            return body.clone();
        }
    }
}
