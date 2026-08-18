package fr.vriege.anilib.feature.extensionrepository.runtime;

import fr.vriege.anilib.foundation.component.ComponentDescriptor;
import fr.vriege.anilib.foundation.validation.Preconditions;
import fr.vriege.anilib.feature.source.CatalogueSource;
import fr.vriege.anilib.feature.source.Source;
import fr.vriege.anilib.feature.source.SourceApiVersion;
import fr.vriege.anilib.feature.source.SourceBrowseRequest;
import fr.vriege.anilib.feature.source.SourceCatalogueItem;
import fr.vriege.anilib.feature.source.SourceCatalogueItemId;
import fr.vriege.anilib.feature.source.SourceContentKind;
import fr.vriege.anilib.feature.source.SourceDescriptor;
import fr.vriege.anilib.feature.source.SourceEpisode;
import fr.vriege.anilib.feature.source.SourceEpisodeId;
import fr.vriege.anilib.feature.source.SourceExtensionManifest;
import fr.vriege.anilib.feature.source.SourceExtensionPlugin;
import fr.vriege.anilib.feature.source.SourceFilterDefinition;
import fr.vriege.anilib.feature.source.SourceId;
import fr.vriege.anilib.feature.source.SourcePage;
import fr.vriege.anilib.feature.source.SourceSearchRequest;
import fr.vriege.anilib.feature.source.SourceStreamFormat;
import fr.vriege.anilib.feature.source.SourceSubtitleTrack;
import fr.vriege.anilib.feature.source.SourceVideoStream;
import fr.vriege.anilib.feature.source.StreamingSource;
import fr.vriege.anilib.kernel.AnilibPlugin;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BooleanSupplier;

/**
 * Adapts one trusted Aniyomi anime source object without linking its external ABI
 * into Anilib's shared modules.
 *
 * <p>The Android boundary is responsible for certificate trust, ABI preflight,
 * class loading, and object construction before calling this adapter.</p>
 */
public final class AniyomiAnimeSourceAdapter {
    private static final SourceApiVersion REQUIRED_API = new SourceApiVersion(1, 4);
    private static final long SUSPEND_TIMEOUT_SECONDS = 60L;

    private AniyomiAnimeSourceAdapter() {
    }

    public static AdaptedSource adapt(
            String packageName,
            String extensionVersion,
            Object delegate) {
        return adapt(packageName, extensionVersion, delegate, () -> true);
    }

    public static AdaptedSource adapt(
            String packageName,
            String extensionVersion,
            Object delegate,
            BooleanSupplier authorized) {
        Preconditions.requireNonBlank(packageName, "packageName");
        Preconditions.requireNonBlank(extensionVersion, "extensionVersion");
        Object source = Preconditions.requireNonNull(delegate, "delegate");
        BooleanSupplier authorization = Preconditions.requireNonNull(authorized, "authorized");
        ReflectedAnimeSource bridge = new ReflectedAnimeSource(
                packageName,
                extensionVersion,
                source,
                authorization);
        SourceExtensionManifest manifest = SourceExtensionManifest.trustedPlatform(
                ComponentDescriptor.of(
                        "extension." + bridge.descriptor().id(),
                        bridge.descriptor().displayName(),
                        extensionVersion),
                bridge.descriptor().id());
        return new AdaptedSource(manifest, bridge);
    }

    public record AdaptedSource(SourceExtensionManifest manifest, Source source) {
        public AdaptedSource {
            Preconditions.requireNonNull(manifest, "manifest");
            Preconditions.requireNonNull(source, "source");
            if (!manifest.sourceId().equals(source.descriptor().id())) {
                throw new IllegalArgumentException("manifest and adapted source IDs must match");
            }
        }

        public AnilibPlugin bundle() {
            return new SourceExtensionPlugin(manifest, ignored -> source);
        }
    }

    private static final class ReflectedAnimeSource implements CatalogueSource, StreamingSource {
        private final Object delegate;
        private final BooleanSupplier authorized;
        private final SourceDescriptor descriptor;
        private final Map<SourceCatalogueItemId, Object> animeById = new ConcurrentHashMap<>();
        private final Map<SourceEpisodeId, Object> episodeById = new ConcurrentHashMap<>();

        private ReflectedAnimeSource(
                String packageName,
                String extensionVersion,
                Object delegate,
                BooleanSupplier authorized) {
            this.delegate = delegate;
            this.authorized = authorized;
            long numericId = number(invoke(delegate, "getId")).longValue();
            SourceId sourceId = SourceId.of(sourceId(packageName, numericId));
            descriptor = new SourceDescriptor(
                    sourceId,
                    text(invoke(delegate, "getName"), "source name"),
                    extensionVersion,
                    language(invokeOptional(delegate, "getLang").orElse("und")),
                    Set.of(SourceContentKind.ANIME),
                    REQUIRED_API);
            requireEitherMethod(delegate, "getPopularAnime", 2, "fetchPopularAnime", 1);
            requireEitherMethod(delegate, "getSearchAnime", 4, "fetchSearchAnime", 3);
            requireEpisodeMethod(delegate);
            requireVideoMethod(delegate);
        }

        @Override
        public SourceDescriptor descriptor() {
            return descriptor;
        }

        @Override
        public SourcePage popular(SourceBrowseRequest request) {
            requireAuthorized();
            Preconditions.requireNonNull(request, "request");
            return page(invokeModernOrRx(delegate, "getPopularAnime", "fetchPopularAnime", request.page()));
        }

        @Override
        public boolean supportsLatest() {
            return invokeOptional(delegate, "getSupportsLatest")
                    .map(AniyomiAnimeSourceAdapter::bool)
                    .orElse(false);
        }

        @Override
        public SourcePage latest(SourceBrowseRequest request) {
            requireAuthorized();
            Preconditions.requireNonNull(request, "request");
            if (!supportsLatest()) {
                return CatalogueSource.super.latest(request);
            }
            return page(invokeModernOrRx(delegate, "getLatestUpdates", "fetchLatestUpdates", request.page()));
        }

        @Override
        public SourcePage search(SourceSearchRequest request) {
            requireAuthorized();
            Preconditions.requireNonNull(request, "request");
            AniyomiAnimeFilterAdapter.ReflectedFilters filters = reflectedFilters();
            filters.apply(request.browseRequest().filters());
            Object result = invokeModernOrRx(
                    delegate,
                    "getSearchAnime",
                    "fetchSearchAnime",
                    request.browseRequest().page(),
                    request.query(),
                    filters.abiValue());
            return page(result);
        }

        @Override
        public List<SourceFilterDefinition> filters() {
            requireAuthorized();
            return reflectedFilters().definitions();
        }

        @Override
        public List<SourceEpisode> episodes(SourceCatalogueItemId itemId) {
            requireAuthorized();
            requireOwned(itemId);
            Object anime = animeById.get(itemId);
            if (anime == null) {
                throw new IllegalStateException(
                        "APK source item must be opened from a catalogue result before loading episodes: "
                                + itemId.value());
            }
            List<?> values = episodeList(anime);
            List<SourceEpisode> episodes = new ArrayList<>(values.size());
            for (Object value : values) {
                String url = text(invoke(value, "getUrl"), "episode URL");
                SourceEpisodeId episodeId = new SourceEpisodeId(itemId, url);
                SourceEpisode episode = new SourceEpisode(
                        episodeId,
                        text(invoke(value, "getName"), "episode name"),
                        episodeNumber(invokeOptional(value, "getEpisode_number").orElse(-1.0d)),
                        uploadedAt(invokeOptional(value, "getDate_upload").orElse(0L)),
                        optionalText(invokeOptional(value, "getScanlator").orElse(null)));
                episodeById.put(episodeId, value);
                episodes.add(episode);
            }
            return List.copyOf(episodes);
        }

        @Override
        public List<SourceVideoStream> streams(SourceEpisodeId episodeId) {
            requireAuthorized();
            requireOwned(episodeId.itemId());
            Object episode = episodeById.get(episodeId);
            if (episode == null) {
                throw new IllegalStateException(
                        "APK source episode must be loaded before resolving streams: " + episodeId.value());
            }
            List<?> values = videoList(episode);
            List<SourceVideoStream> streams = new ArrayList<>(values.size());
            for (int index = 0; index < values.size(); index++) {
                streams.add(stream(values.get(index), index));
            }
            return List.copyOf(streams);
        }

        private List<?> episodeList(Object anime) {
            if (hasMethod(delegate, "getAnimeEpisodeUpdate", 5)) {
                Object update = invokeSuspend(
                        delegate,
                        "getAnimeEpisodeUpdate",
                        anime,
                        List.of(),
                        false,
                        true);
                return list(invoke(update, "getEpisodes"), "episode update");
            }
            if (hasMethod(delegate, "getEpisodeList", 2)) {
                return list(invokeSuspend(delegate, "getEpisodeList", anime), "episode list");
            }
            return list(await(invoke(delegate, "fetchEpisodeList", anime)), "episode list");
        }

        private List<?> videoList(Object episode) {
            if (supportsHosters(delegate) && hasCompatibleSuspendMethod(delegate, "getHosterList", episode)) {
                List<?> hosters = list(invokeSuspend(delegate, "getHosterList", episode), "hoster list");
                List<Object> videos = new ArrayList<>();
                for (Object hoster : hosters) {
                    Optional<Object> eagerVideos = invokeOptional(hoster, "getVideoList");
                    if (eagerVideos.isPresent()) {
                        videos.addAll(list(eagerVideos.get(), "hoster video list"));
                    } else {
                        videos.addAll(list(invokeSuspend(delegate, "getVideoList", hoster), "hoster video list"));
                    }
                }
                return List.copyOf(videos);
            }
            if (hasCompatibleSuspendMethod(delegate, "getVideoList", episode)) {
                return list(invokeSuspend(delegate, "getVideoList", episode), "video list");
            }
            return list(await(invoke(delegate, "fetchVideoList", episode)), "video list");
        }

        private AniyomiAnimeFilterAdapter.ReflectedFilters reflectedFilters() {
            return AniyomiAnimeFilterAdapter.from(invoke(delegate, "getFilterList"));
        }

        private SourcePage page(Object value) {
            List<?> values = list(invoke(value, "getAnimes"), "anime page");
            List<SourceCatalogueItem> items = new ArrayList<>(values.size());
            for (Object anime : values) {
                String url = text(invoke(anime, "getUrl"), "anime URL");
                SourceCatalogueItemId itemId = new SourceCatalogueItemId(descriptor.id(), url);
                SourceCatalogueItem item = new SourceCatalogueItem(
                        itemId,
                        text(invoke(anime, "getTitle"), "anime title"),
                        nullableText(invokeOptional(anime, "getDescription").orElse(null)),
                        absoluteUri(invokeOptional(anime, "getThumbnail_url").orElse(null)),
                        SourceContentKind.ANIME);
                animeById.put(itemId, anime);
                items.add(item);
            }
            return new SourcePage(items, bool(invoke(value, "getHasNextPage")));
        }

        private SourceVideoStream stream(Object value, int index) {
            String locationText = firstText(value, "getVideoUrl", "getUrl");
            URI location = absoluteUri(locationText)
                    .orElseThrow(() -> new IllegalStateException("APK video location must be absolute"));
            String quality = firstText(value, "getVideoTitle", "getQuality");
            Map<String, String> headers = headers(invokeOptional(value, "getHeaders").orElse(null));
            List<SourceSubtitleTrack> subtitles = subtitles(
                    invokeOptional(value, "getSubtitleTracks").orElse(List.of()),
                    headers,
                    index);
            return new SourceVideoStream(
                    "stream-" + index,
                    quality,
                    location,
                    streamFormat(location),
                    headers,
                    subtitles);
        }

        private List<SourceSubtitleTrack> subtitles(
                Object value,
                Map<String, String> headers,
                int streamIndex) {
            List<?> values = list(value, "subtitle tracks");
            List<SourceSubtitleTrack> subtitles = new ArrayList<>(values.size());
            for (int index = 0; index < values.size(); index++) {
                Object track = values.get(index);
                String language = nullableText(invokeOptional(track, "getLang").orElse(null));
                URI location = absoluteUri(invoke(track, "getUrl"))
                        .orElseThrow(() -> new IllegalStateException("APK subtitle location must be absolute"));
                subtitles.add(new SourceSubtitleTrack(
                        "subtitle-" + streamIndex + "-" + index,
                        language.isBlank() ? "Subtitle " + (index + 1) : language,
                        language.isBlank() ? Optional.empty() : Optional.of(language),
                        location,
                        headers));
            }
            return List.copyOf(subtitles);
        }

        private void requireOwned(SourceCatalogueItemId itemId) {
            Preconditions.requireNonNull(itemId, "itemId");
            if (!descriptor.id().equals(itemId.sourceId())) {
                throw new IllegalArgumentException("item belongs to a different source");
            }
        }

        private void requireAuthorized() {
            if (!authorized.getAsBoolean()) {
                throw new SecurityException("APK extension certificate trust is no longer active");
            }
        }
    }

    private static Object await(Object value) {
        Optional<Object> blocking = invokeOptional(value, "toBlocking");
        return blocking.map(result -> invoke(result, "single")).orElse(value);
    }

    private static Object invokeModernOrRx(
            Object target,
            String suspendMethod,
            String rxMethod,
            Object... arguments) {
        if (hasCompatibleSuspendMethod(target, suspendMethod, arguments)) {
            return invokeSuspend(target, suspendMethod, arguments);
        }
        return await(invoke(target, rxMethod, arguments));
    }

    private static Object invokeSuspend(Object target, String methodName, Object... arguments) {
        Object value = Preconditions.requireNonNull(target, "reflection target");
        Method method = compatibleSuspendMethod(value.getClass(), methodName, arguments)
                .orElseThrow(() -> new IllegalStateException(
                        "Aniyomi suspend ABI method is missing: " + value.getClass().getName() + "." + methodName));
        CoroutineCompletion completion = CoroutineCompletion.create(method.getParameterTypes()[arguments.length]);
        Object[] callArguments = new Object[arguments.length + 1];
        System.arraycopy(arguments, 0, callArguments, 0, arguments.length);
        callArguments[arguments.length] = completion.continuation();
        Object result = invokeMethod(value, method, callArguments);
        if (!isCoroutineSuspended(result, method.getDeclaringClass().getClassLoader())) {
            return result;
        }
        return completion.await(methodName);
    }

    private static Object invokeMethod(Object target, Method method, Object[] arguments) {
        try {
            return method.invoke(target, arguments);
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException("Aniyomi ABI method is inaccessible: " + method.getName(), exception);
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause() == null ? exception : exception.getCause();
            throw new IllegalStateException("Aniyomi source call failed: " + method.getName(), cause);
        }
    }

    private static boolean isCoroutineSuspended(Object value, ClassLoader classLoader) {
        if (value == null) {
            return false;
        }
        try {
            Class<?> intrinsics = Class.forName(
                    "kotlin.coroutines.intrinsics.IntrinsicsKt",
                    false,
                    classLoader);
            Object marker = intrinsics.getMethod("getCOROUTINE_SUSPENDED").invoke(null);
            return marker == value;
        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException ignored) {
            return "COROUTINE_SUSPENDED".equals(String.valueOf(value));
        } catch (InvocationTargetException exception) {
            throw new IllegalStateException("Unable to inspect Kotlin coroutine state", exception.getCause());
        }
    }

    static Object invoke(Object target, String methodName, Object... arguments) {
        Object value = Preconditions.requireNonNull(target, "reflection target");
        Method method = compatibleMethod(value.getClass(), methodName, arguments)
                .orElseThrow(() -> new IllegalStateException(
                        "Aniyomi ABI method is missing: " + value.getClass().getName() + "." + methodName));
        return invokeMethod(value, method, arguments);
    }

    static Optional<Object> invokeOptional(Object target, String methodName) {
        if (target == null) {
            return Optional.empty();
        }
        Optional<Method> method = compatibleMethod(target.getClass(), methodName, new Object[0]);
        return method.map(ignored -> invoke(target, methodName));
    }

    private static void requireEitherMethod(
            Object target,
            String firstMethod,
            int firstParameterCount,
            String secondMethod,
            int secondParameterCount) {
        if (!hasMethod(target, firstMethod, firstParameterCount)
                && !hasMethod(target, secondMethod, secondParameterCount)) {
            throw new IllegalArgumentException(
                    "Object is not a supported Aniyomi anime source; missing "
                            + firstMethod + " or " + secondMethod);
        }
    }

    private static void requireEpisodeMethod(Object target) {
        if (!hasMethod(target, "getAnimeEpisodeUpdate", 5)
                && !hasMethod(target, "getEpisodeList", 2)
                && !hasMethod(target, "fetchEpisodeList", 1)) {
            throw new IllegalArgumentException(
                    "Object is not a supported Aniyomi anime source; missing episode API");
        }
    }

    private static void requireVideoMethod(Object target) {
        if (!hasMethod(target, "getHosterList", 2)
                && !hasMethod(target, "getVideoList", 2)
                && !hasMethod(target, "fetchVideoList", 1)) {
            throw new IllegalArgumentException(
                    "Object is not a supported Aniyomi anime source; missing video API");
        }
    }

    private static boolean hasMethod(Object target, String methodName, int parameterCount) {
        return List.of(target.getClass().getMethods()).stream()
                .anyMatch(method -> method.getName().equals(methodName)
                        && method.getParameterCount() == parameterCount);
    }

    private static boolean supportsHosters(Object target) {
        Class<?> current = target.getClass();
        while (current != null && !current.equals(Object.class)) {
            String name = current.getName();
            if (name.equals("eu.kanade.tachiyomi.animesource.online.AnimeHttpSource")
                    || name.equals("eu.kanade.tachiyomi.animesource.online.ParsedAnimeHttpSource")) {
                return false;
            }
            boolean declared = List.of(current.getDeclaredMethods()).stream()
                    .map(Method::getName)
                    .anyMatch(Set.of("getHosterList", "hosterListRequest", "hosterListParse")::contains);
            if (declared) {
                return true;
            }
            current = current.getSuperclass();
        }
        return false;
    }

    private static boolean hasCompatibleSuspendMethod(Object target, String methodName, Object... arguments) {
        return compatibleSuspendMethod(target.getClass(), methodName, arguments).isPresent();
    }

    private static Optional<Method> compatibleSuspendMethod(
            Class<?> type,
            String methodName,
            Object[] arguments) {
        for (Method method : type.getMethods()) {
            Class<?>[] parameterTypes = method.getParameterTypes();
            if (method.getName().equals(methodName)
                    && parameterTypes.length == arguments.length + 1
                    && continuationType(parameterTypes[parameterTypes.length - 1])
                    && compatiblePrefix(parameterTypes, arguments)) {
                return Optional.of(method);
            }
        }
        return Optional.empty();
    }

    private static boolean continuationType(Class<?> type) {
        return type.getName().equals("kotlin.coroutines.Continuation") || type.equals(Object.class);
    }

    private static boolean compatiblePrefix(Class<?>[] parameterTypes, Object[] arguments) {
        Class<?>[] prefix = new Class<?>[arguments.length];
        System.arraycopy(parameterTypes, 0, prefix, 0, arguments.length);
        return compatible(prefix, arguments);
    }

    private static Optional<Method> compatibleMethod(
            Class<?> type,
            String methodName,
            Object[] arguments) {
        for (Method method : type.getMethods()) {
            if (method.getName().equals(methodName)
                    && method.getParameterCount() == arguments.length
                    && compatible(method.getParameterTypes(), arguments)) {
                return Optional.of(method);
            }
        }
        return Optional.empty();
    }

    private static boolean compatible(Class<?>[] parameterTypes, Object[] arguments) {
        for (int index = 0; index < parameterTypes.length; index++) {
            Object argument = arguments[index];
            if (argument == null) {
                if (parameterTypes[index].isPrimitive()) {
                    return false;
                }
            } else if (!boxed(parameterTypes[index]).isInstance(argument)) {
                return false;
            }
        }
        return true;
    }

    private static Class<?> boxed(Class<?> type) {
        if (!type.isPrimitive()) {
            return type;
        }
        return switch (type.getName()) {
            case "boolean" -> Boolean.class;
            case "byte" -> Byte.class;
            case "char" -> Character.class;
            case "double" -> Double.class;
            case "float" -> Float.class;
            case "int" -> Integer.class;
            case "long" -> Long.class;
            case "short" -> Short.class;
            default -> type;
        };
    }

    static List<?> list(Object value, String label) {
        if (value instanceof List<?> values) {
            return values;
        }
        throw new IllegalStateException(label + " must be a List");
    }

    static Number number(Object value) {
        if (value instanceof Number number) {
            return number;
        }
        throw new IllegalStateException("Aniyomi numeric property has an invalid type");
    }

    static boolean bool(Object value) {
        if (value instanceof Boolean result) {
            return result;
        }
        throw new IllegalStateException("Aniyomi boolean property has an invalid type");
    }

    private static String text(Object value, String label) {
        if (value instanceof String text && !text.isBlank()) {
            return text;
        }
        throw new IllegalStateException(label + " must be non-blank");
    }

    static String nullableText(Object value) {
        return value instanceof String text ? text : "";
    }

    private static Optional<String> optionalText(Object value) {
        String text = nullableText(value).strip();
        return text.isEmpty() ? Optional.empty() : Optional.of(text);
    }

    private static String firstText(Object target, String... methods) {
        for (String method : methods) {
            Optional<Object> value = invokeOptional(target, method);
            if (value.isPresent() && value.get() instanceof String text && !text.isBlank()) {
                return text;
            }
        }
        throw new IllegalStateException("Aniyomi text property is missing: " + String.join(" or ", methods));
    }

    private static Optional<URI> absoluteUri(Object value) {
        if (!(value instanceof String text) || text.isBlank()) {
            return Optional.empty();
        }
        try {
            URI uri = URI.create(text.strip());
            if (!uri.isAbsolute()) {
                return Optional.empty();
            }
            String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
            return Set.of("http", "https", "file").contains(scheme) ? Optional.of(uri) : Optional.empty();
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }

    private static Optional<Instant> uploadedAt(Object value) {
        if (!(value instanceof Number number) || number.longValue() <= 0) {
            return Optional.empty();
        }
        return Optional.of(Instant.ofEpochMilli(number.longValue()));
    }

    private static double episodeNumber(Object value) {
        if (!(value instanceof Number number) || !Double.isFinite(number.doubleValue())) {
            return SourceEpisode.UNKNOWN_NUMBER;
        }
        return Math.max(SourceEpisode.UNKNOWN_NUMBER, number.doubleValue());
    }

    private static Map<String, String> headers(Object value) {
        if (value == null) {
            return Map.of();
        }
        Object entries = invokeOptional(value, "toMultimap").orElse(value);
        if (!(entries instanceof Map<?, ?> map)) {
            throw new IllegalStateException("Aniyomi video headers must expose a map");
        }
        Map<String, String> headers = new LinkedHashMap<>();
        map.forEach((name, headerValues) -> {
            if (name instanceof String headerName && !headerName.isBlank()) {
                String headerValue = headerValue(headerValues);
                if (!headerValue.isBlank()) {
                    headers.put(headerName, headerValue);
                }
            }
        });
        return Map.copyOf(headers);
    }

    private static String headerValue(Object value) {
        if (value instanceof List<?> values) {
            return values.stream().map(String::valueOf).reduce((left, right) -> left + ", " + right).orElse("");
        }
        return value == null ? "" : String.valueOf(value);
    }

    private static SourceStreamFormat streamFormat(URI location) {
        String path = Optional.ofNullable(location.getPath()).orElse("").toLowerCase(Locale.ROOT);
        if (path.endsWith(".m3u8")) {
            return SourceStreamFormat.HLS;
        }
        if (path.endsWith(".mpd")) {
            return SourceStreamFormat.DASH;
        }
        return SourceStreamFormat.AUTOMATIC;
    }

    private static String sourceId(String packageName, long numericId) {
        String normalizedPackage = packageName.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9.-]+", "-")
                .replaceAll("[.-]{2,}", ".")
                .replaceAll("^[^a-z]+", "");
        if (normalizedPackage.isBlank()) {
            throw new IllegalArgumentException("packageName cannot produce a stable source ID");
        }
        return "apk." + normalizedPackage + "." + Long.toUnsignedString(numericId);
    }

    private static String language(Object value) {
        String language = nullableText(value).strip();
        return language.isEmpty() || language.equalsIgnoreCase("all") ? "und" : language;
    }

    private static final class CoroutineCompletion {
        private final Object continuation;
        private final CompletableFuture<Object> result;

        private CoroutineCompletion(Object continuation, CompletableFuture<Object> result) {
            this.continuation = continuation;
            this.result = result;
        }

        private static CoroutineCompletion create(Class<?> continuationType) {
            CompletableFuture<Object> result = new CompletableFuture<>();
            if (continuationType.equals(Object.class)) {
                return new CoroutineCompletion(new Object(), result);
            }
            ClassLoader classLoader = continuationType.getClassLoader();
            Object continuation = Proxy.newProxyInstance(
                    classLoader,
                    new Class<?>[]{continuationType},
                    (proxy, method, arguments) -> switch (method.getName()) {
                        case "resumeWith" -> {
                            complete(result, arguments == null ? null : arguments[0], classLoader);
                            yield null;
                        }
                        case "getContext" -> emptyCoroutineContext(classLoader);
                        case "toString" -> "AnilibAniyomiContinuation";
                        case "hashCode" -> System.identityHashCode(proxy);
                        case "equals" -> proxy == (arguments == null ? null : arguments[0]);
                        default -> throw new UnsupportedOperationException(
                                "Unsupported Kotlin continuation method: " + method.getName());
                    });
            return new CoroutineCompletion(continuation, result);
        }

        private static void complete(
                CompletableFuture<Object> result,
                Object value,
                ClassLoader classLoader) {
            try {
                Class<?> resultSupport = Class.forName("kotlin.ResultKt", false, classLoader);
                resultSupport.getMethod("throwOnFailure", Object.class).invoke(null, value);
                result.complete(value);
            } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException exception) {
                result.completeExceptionally(new IllegalStateException(
                        "Kotlin coroutine result support is unavailable",
                        exception));
            } catch (InvocationTargetException exception) {
                result.completeExceptionally(exception.getCause() == null ? exception : exception.getCause());
            }
        }

        private static Object emptyCoroutineContext(ClassLoader classLoader) {
            try {
                Class<?> context = Class.forName("kotlin.coroutines.EmptyCoroutineContext", false, classLoader);
                return context.getField("INSTANCE").get(null);
            } catch (ClassNotFoundException | NoSuchFieldException | IllegalAccessException exception) {
                throw new IllegalStateException("Kotlin empty coroutine context is unavailable", exception);
            }
        }

        private Object continuation() {
            return continuation;
        }

        private Object await(String methodName) {
            try {
                return result.get(SUSPEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Aniyomi suspend call was interrupted: " + methodName, exception);
            } catch (ExecutionException exception) {
                Throwable cause = exception.getCause() == null ? exception : exception.getCause();
                throw new IllegalStateException("Aniyomi suspend call failed: " + methodName, cause);
            } catch (TimeoutException exception) {
                throw new IllegalStateException("Aniyomi suspend call timed out: " + methodName, exception);
            }
        }
    }
}
