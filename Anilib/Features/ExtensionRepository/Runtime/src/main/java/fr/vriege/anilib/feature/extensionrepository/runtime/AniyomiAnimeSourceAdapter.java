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
import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
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
            requireMethod(delegate, "fetchPopularAnime", 1);
            requireMethod(delegate, "fetchSearchAnime", 3);
            requireMethod(delegate, "fetchEpisodeList", 1);
            requireMethod(delegate, "fetchVideoList", 1);
        }

        @Override
        public SourceDescriptor descriptor() {
            return descriptor;
        }

        @Override
        public SourcePage popular(SourceBrowseRequest request) {
            requireAuthorized();
            Preconditions.requireNonNull(request, "request");
            return page(await(invoke(delegate, "fetchPopularAnime", request.page())));
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
            return page(await(invoke(delegate, "fetchLatestUpdates", request.page())));
        }

        @Override
        public SourcePage search(SourceSearchRequest request) {
            requireAuthorized();
            Preconditions.requireNonNull(request, "request");
            Object filters = invoke(delegate, "getFilterList");
            Object result = invoke(
                    delegate,
                    "fetchSearchAnime",
                    request.browseRequest().page(),
                    request.query(),
                    filters);
            return page(await(result));
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
            List<?> values = list(await(invoke(delegate, "fetchEpisodeList", anime)), "episode list");
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
            List<?> values = list(await(invoke(delegate, "fetchVideoList", episode)), "video list");
            List<SourceVideoStream> streams = new ArrayList<>(values.size());
            for (int index = 0; index < values.size(); index++) {
                streams.add(stream(values.get(index), index));
            }
            return List.copyOf(streams);
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

    private static Object invoke(Object target, String methodName, Object... arguments) {
        Object value = Preconditions.requireNonNull(target, "reflection target");
        Method method = compatibleMethod(value.getClass(), methodName, arguments)
                .orElseThrow(() -> new IllegalStateException(
                        "Aniyomi ABI method is missing: " + value.getClass().getName() + "." + methodName));
        try {
            return method.invoke(value, arguments);
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException("Aniyomi ABI method is inaccessible: " + methodName, exception);
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause() == null ? exception : exception.getCause();
            throw new IllegalStateException("Aniyomi source call failed: " + methodName, cause);
        }
    }

    private static Optional<Object> invokeOptional(Object target, String methodName) {
        if (target == null) {
            return Optional.empty();
        }
        Optional<Method> method = compatibleMethod(target.getClass(), methodName, new Object[0]);
        return method.map(ignored -> invoke(target, methodName));
    }

    private static void requireMethod(Object target, String methodName, int parameterCount) {
        boolean present = List.of(target.getClass().getMethods()).stream()
                .anyMatch(method -> method.getName().equals(methodName)
                        && method.getParameterCount() == parameterCount);
        if (!present) {
            throw new IllegalArgumentException(
                    "Object is not a supported Aniyomi anime source; missing " + methodName);
        }
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

    private static List<?> list(Object value, String label) {
        if (value instanceof List<?> values) {
            return values;
        }
        throw new IllegalStateException(label + " must be a List");
    }

    private static Number number(Object value) {
        if (value instanceof Number number) {
            return number;
        }
        throw new IllegalStateException("Aniyomi numeric property has an invalid type");
    }

    private static boolean bool(Object value) {
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

    private static String nullableText(Object value) {
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
}
