package fr.vriege.anilib.feature.extensionrepository.runtime;

import fr.vriege.anilib.feature.source.CatalogueSource;
import fr.vriege.anilib.feature.source.PagedSource;
import fr.vriege.anilib.feature.source.Source;
import fr.vriege.anilib.feature.source.SourceApiVersion;
import fr.vriege.anilib.feature.source.SourceBrowseRequest;
import fr.vriege.anilib.feature.source.SourceCatalogueItem;
import fr.vriege.anilib.feature.source.SourceCatalogueItemId;
import fr.vriege.anilib.feature.source.SourceContentKind;
import fr.vriege.anilib.feature.source.SourceContentUnit;
import fr.vriege.anilib.feature.source.SourceContentUnitId;
import fr.vriege.anilib.feature.source.SourceDescriptor;
import fr.vriege.anilib.feature.source.SourceEpisode;
import fr.vriege.anilib.feature.source.SourceEpisodeId;
import fr.vriege.anilib.feature.source.SourceExtensionManifest;
import fr.vriege.anilib.feature.source.SourceExtensionPlugin;
import fr.vriege.anilib.feature.source.SourceId;
import fr.vriege.anilib.feature.source.SourcePage;
import fr.vriege.anilib.feature.source.SourcePageResource;
import fr.vriege.anilib.feature.source.SourceSearchRequest;
import fr.vriege.anilib.feature.source.SourceStreamFormat;
import fr.vriege.anilib.feature.source.SourceSubtitleTrack;
import fr.vriege.anilib.feature.source.SourceVideoStream;
import fr.vriege.anilib.feature.source.StreamingSource;
import fr.vriege.anilib.feature.source.WebSource;
import fr.vriege.anilib.feature.source.DetailedSource;
import fr.vriege.anilib.feature.source.SourcePublicationStatus;
import fr.vriege.anilib.feature.source.SourceTitleDetails;
import fr.vriege.anilib.feature.source.SourcePreferenceDefinition;
import fr.vriege.anilib.feature.source.SourcePreferenceType;
import fr.vriege.anilib.foundation.component.ComponentDescriptor;
import fr.vriege.anilib.foundation.validation.Preconditions;
import fr.vriege.anilib.framework.http.AnilibHttpClient;
import fr.vriege.anilib.framework.http.HttpMethod;
import fr.vriege.anilib.framework.http.HttpRequest;
import fr.vriege.anilib.framework.http.HttpResponse;
import fr.vriege.anilib.kernel.AnilibPlugin;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class DesktopExtensionSourceBridge {
    private static final SourceApiVersion REQUIRED_API = new SourceApiVersion(1, 8);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(45);
    private static final int MAX_RETAINED_MODELS = 4_096;

    private final URI baseUri;
    private final AnilibHttpClient client;
    private volatile Map<String, String> compatibilityFailures = Map.of();

    public DesktopExtensionSourceBridge(URI baseUri, AnilibHttpClient client) {
        this.baseUri = requireLoopback(baseUri);
        this.client = Preconditions.requireNonNull(client, "client");
    }

    public void requireHealthy() {
        Map<String, Object> health = object(get("/api/v1/health", Map.of()));
        String service = text(health, "service");
        if (!"ok".equals(text(health, "status"))
                || !"anilib-desktop-extension-host".equals(service)) {
            throw new IllegalStateException("Extension engine returned an unexpected health response");
        }
    }

    public List<AnilibPlugin> sourceBundles() {
        Map<String, Object> document = object(get("/api/v1/sources", Map.of()));
        compatibilityFailures = stringMap(document.get("failures"));
        List<AnilibPlugin> bundles = new ArrayList<>();
        for (Object value : array(document, "manga")) {
            bundles.add(bundle(new MangaBridge(source(object(value), SourceContentKind.MANGA))));
        }
        for (Object value : array(document, "anime")) {
            bundles.add(bundle(new AnimeBridge(source(object(value), SourceContentKind.ANIME))));
        }
        return List.copyOf(bundles);
    }

    public Map<String, String> compatibilityFailures() {
        return compatibilityFailures;
    }

    public Set<String> installedPackageNames() {
        Map<String, Object> document = object(get("/api/v1/extensions/installed", Map.of()));
        Set<String> packages = new java.util.LinkedHashSet<>();
        for (Object value : array(document, "extensions")) {
            packages.add(text(object(value), "pkg"));
        }
        return Set.copyOf(packages);
    }

    public void saveRepositories(List<URI> repositories) {
        List<URI> values = List.copyOf(Preconditions.requireNonNull(repositories, "repositories"));
        String body = "{\"repos\":[" + String.join(",", values.stream()
                .map(URI::toASCIIString)
                .map(DesktopExtensionSourceBridge::jsonString)
                .toList()) + "]}";
        object(post("/api/v1/extensions/repos", body));
    }

    public String install(URI apkUri) {
        URI uri = Preconditions.requireNonNull(apkUri, "apkUri").normalize();
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) {
            throw new IllegalArgumentException("APK location must use HTTPS");
        }
        String body = "{\"repoUrl\":\"\",\"apk\":" + jsonString(uri.toASCIIString()) + "}";
        Map<String, Object> result = object(post("/api/v1/extensions/install", body));
        if (!booleanValue(result.get("ok"))) {
            throw new IllegalStateException(optionalText(result, "error").orElse("Extension engine rejected the APK"));
        }
        String name = optionalText(result, "name").orElse(uri.getPath());
        return name + " installed for desktop.";
    }

    public String uninstall(String packageName) {
        String name = Preconditions.requireNonBlank(packageName, "packageName");
        Map<String, Object> result = object(post(
                "/api/v1/extensions/uninstall",
                "{\"pkg\":" + jsonString(name) + "}"));
        if (!booleanValue(result.get("ok"))) {
            throw new IllegalStateException(optionalText(result, "error")
                    .orElse("Extension engine rejected the uninstall request"));
        }
        return name + " uninstalled from the desktop engine.";
    }

    private AnilibPlugin bundle(Source source) {
        SourceDescriptor descriptor = source.descriptor();
        SourceExtensionManifest manifest = SourceExtensionManifest.trustedPlatform(
                ComponentDescriptor.of(
                        "extension." + descriptor.id(),
                        descriptor.displayName(),
                        descriptor.extensionVersion()),
                descriptor.id());
        return new SourceExtensionPlugin(manifest, ignored -> source);
    }

    private RemoteSource source(Map<String, Object> value, SourceContentKind kind) {
        String numericId = text(value, "id");
        long parsedId;
        try {
            parsedId = Long.parseUnsignedLong(numericId);
        } catch (NumberFormatException exception) {
            throw new IllegalStateException("Extension engine source id is not an unsigned 64-bit integer", exception);
        }
        SourceId sourceId = SourceId.of(AniyomiAnimeSourceAdapter.sourceId(parsedId));
        String language = optionalText(value, "lang")
                .filter(item -> !item.equalsIgnoreCase("all"))
                .orElse("und");
        String packageName = optionalText(value, "pkg").orElse("external-apk");
        SourceDescriptor descriptor = new SourceDescriptor(
                sourceId,
                text(value, "name"),
                packageName,
                language,
                Set.of(kind),
                REQUIRED_API);
        return new RemoteSource(numericId, descriptor, webUri(value.get("baseUrl")), retainedModels());
    }

    private SourcePage catalogue(RemoteSource source, String operation, int page, String query) {
        Map<String, String> parameters = new LinkedHashMap<>();
        parameters.put("page", Integer.toString(page));
        if (query != null) {
            parameters.put("query", query);
        }
        String kind = source.descriptor.contentKinds().contains(SourceContentKind.MANGA) ? "manga" : "anime";
        Map<String, Object> document = object(get(
                "/api/v1/" + kind + "/" + encode(source.remoteId) + "/" + operation,
                parameters));
        String collection = kind.equals("manga") ? "mangas" : "animes";
        Map<SourceCatalogueItemId, SourceCatalogueItem> unique = new LinkedHashMap<>();
        for (Object value : array(document, collection)) {
            Map<String, Object> item = object(value);
            String url = text(item, "url");
            SourceCatalogueItemId id = new SourceCatalogueItemId(source.descriptor.id(), url);
            SourceCatalogueItem mapped = new SourceCatalogueItem(
                    id,
                    text(item, "title"),
                    optionalText(item, "description").orElse(""),
                    proxyImage(source, item.get("thumbnail_url")),
                    source.descriptor.contentKinds().iterator().next());
            source.models.put(id, new CatalogueModel(
                    mapped.title(),
                    mapped.description(),
                    optionalText(item, "thumbnail_url").orElse(""),
                    optionalText(item, "artist").orElse(""),
                    optionalText(item, "author").orElse(""),
                    optionalText(item, "genre").orElse(""),
                    (int) longValue(item.get("status"))));
            unique.putIfAbsent(id, mapped);
        }
        return new SourcePage(List.copyOf(unique.values()), booleanValue(document.get("hasNextPage")));
    }

    private List<SourcePreferenceDefinition> preferences(RemoteSource source) {
        Map<String, Object> document = object(get(
                "/api/v1/sources/" + encode(source.remoteId) + "/prefs", Map.of()));
        Object raw = document.get("prefs");
        if (!(raw instanceof List<?> values)) {
            return List.of();
        }
        List<SourcePreferenceDefinition> result = new ArrayList<>();
        for (Object entry : values) {
            Map<String, Object> value = object(entry);
            String id = text(value, "key");
            String title = optionalText(value, "title").orElse(id);
            String summary = optionalText(value, "summary").orElse("");
            String type = optionalText(value, "type").orElse("text").toLowerCase(Locale.ROOT);
            String current = String.valueOf(value.getOrDefault("value", ""));
            List<String> options = splitValues(value.get("values"));
            SourcePreferenceType mapped = switch (type) {
                case "boolean", "switch" -> SourcePreferenceType.SWITCH;
                case "list", "select" -> SourcePreferenceType.SELECT;
                default -> SourcePreferenceType.TEXT;
            };
            String defaultValue = switch (mapped) {
                case SWITCH -> Boolean.toString(Boolean.parseBoolean(current));
                case SELECT -> options.contains(current) ? current : options.isEmpty() ? "" : options.getFirst();
                case TEXT -> current.equals("null") ? "" : current;
            };
            if (mapped != SourcePreferenceType.SELECT || !options.isEmpty()) {
                result.add(new SourcePreferenceDefinition(
                        id, title, summary, mapped, options, defaultValue, false));
            }
        }
        return List.copyOf(result);
    }

    private Optional<URI> homePage(RemoteSource source) {
        return source.homePage;
    }

    private Optional<URI> titlePage(RemoteSource source, SourceCatalogueItemId itemId) {
        requireOwned(source, itemId);
        Optional<URI> absolute = webUri(itemId.value());
        if (absolute.isPresent()) {
            return absolute;
        }
        try {
            URI relative = URI.create(itemId.value());
            return homePage(source).map(base -> base.resolve(relative));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }

    private void applyPreferences(RemoteSource source, Map<String, String> values) {
        if (values.isEmpty()) {
            return;
        }
        String body = "{" + String.join(",", values.entrySet().stream()
                .map(entry -> jsonString(entry.getKey()) + ":" + preferenceJson(entry.getValue()))
                .toList()) + "}";
        Map<String, Object> result = object(post(
                "/api/v1/sources/" + encode(source.remoteId) + "/prefs", body));
        if (!booleanValue(result.get("ok"))) {
            throw new IllegalStateException(optionalText(result, "error")
                    .orElse("Extension engine rejected source preferences"));
        }
    }

    private static String preferenceJson(String value) {
        if (value.equals("true") || value.equals("false")) {
            return value;
        }
        return jsonString(value);
    }

    private Object get(String path, Map<String, String> parameters) {
        URI uri = endpoint(path, parameters);
        return decode(client.execute(HttpRequest.builder(uri).timeout(REQUEST_TIMEOUT).build()));
    }

    private Object post(String path, String json) {
        HttpRequest request = HttpRequest.builder(endpoint(path, Map.of()))
                .method(HttpMethod.POST)
                .header("content-type", "application/json")
                .body(json.getBytes(StandardCharsets.UTF_8))
                .timeout(REQUEST_TIMEOUT)
                .build();
        return decode(client.execute(request));
    }

    private byte[] bytes(String path, Map<String, String> parameters) {
        HttpResponse response = client.execute(HttpRequest.builder(endpoint(path, parameters))
                .timeout(REQUEST_TIMEOUT)
                .build());
        requireSuccess(response);
        return response.body();
    }

    private Object decode(HttpResponse response) {
        requireSuccess(response);
        try {
            return JsonParser.parse(response.bodyAsUtf8());
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("Extension engine returned invalid JSON", exception);
        }
    }

    private static void requireSuccess(HttpResponse response) {
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw failure(response);
        }
    }

    private static IllegalStateException failure(HttpResponse response) {
        String fallback = "The desktop extension host could not complete the request";
        try {
            Map<String, Object> document = object(JsonParser.parse(response.bodyAsUtf8()));
            Map<String, Object> error = object(document.get("error"));
            String code = optionalText(error, "code").orElse("internal_host_failure");
            String correlationId = optionalText(error, "correlationId").orElse("");
            String message = switch (code) {
                case "unsupported_capability" -> "This extension does not support this operation on desktop";
                case "remote_http_failure" -> "The source website could not complete the request";
                case "parse_failure" -> "The extension could not read the source response";
                case "abi_failure" -> "This extension is not compatible with the desktop host";
                case "invalid_request" -> "The desktop extension host rejected the request";
                default -> optionalText(error, "message").orElse(fallback);
            };
            return new IllegalStateException(message + (correlationId.isBlank()
                    ? "" : ". Diagnostic ID: " + correlationId));
        } catch (RuntimeException ignored) {
            return new IllegalStateException(fallback);
        }
    }

    private URI endpoint(String path, Map<String, String> parameters) {
        String query = String.join("&", parameters.entrySet().stream()
                .map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
                .toList());
        return baseUri.resolve(path + (query.isEmpty() ? "" : "?" + query));
    }

    private Optional<URI> proxyImage(RemoteSource source, Object value) {
        return webUri(value).map(location -> endpoint("/api/v1/proxy", Map.of(
                "sourceId", source.remoteId,
                "url", location.toASCIIString())));
    }

    private SourceTitleDetails details(RemoteSource source, SourceCatalogueItem item) {
        SourceCatalogueItemId itemId = item.id();
        requireOwned(source, itemId);
        source.models.putIfAbsent(itemId, new CatalogueModel(
                item.title(), item.description(), originalImage(source, item.thumbnail()).orElse(""),
                "", "", "", -1));
        String kind = source.descriptor.contentKinds().contains(SourceContentKind.MANGA) ? "manga" : "anime";
        Map<String, Object> document = object(get(
                "/api/v1/" + kind + "/" + encode(source.remoteId) + "/details",
                modelParameters(source, itemId)));
        Object nested = document.get(kind);
        Map<String, Object> value = nested instanceof Map<?, ?> ? object(nested) : document;
        return new SourceTitleDetails(
                itemId,
                text(value, "title"),
                optionalText(value, "description").orElse(""),
                splitValues(value.get("author")),
                splitValues(value.get("artist")),
                splitValues(value.get("genre")),
                publicationStatus(value.get("status")),
                proxyImage(source, value.get("thumbnail_url")),
                source.descriptor.contentKinds().iterator().next());
    }

    private static List<String> splitValues(Object value) {
        if (value instanceof List<?> values) {
            return values.stream().filter(String.class::isInstance).map(String.class::cast)
                    .map(String::strip).filter(item -> !item.isEmpty()).toList();
        }
        if (value instanceof String text) {
            return java.util.Arrays.stream(text.split(","))
                    .map(String::strip).filter(item -> !item.isEmpty()).toList();
        }
        return List.of();
    }

    private static SourcePublicationStatus publicationStatus(Object value) {
        return switch ((int) longValue(value)) {
            case 1 -> SourcePublicationStatus.ONGOING;
            case 2 -> SourcePublicationStatus.COMPLETED;
            case 3 -> SourcePublicationStatus.LICENSED;
            case 4 -> SourcePublicationStatus.FINISHED;
            case 5 -> SourcePublicationStatus.CANCELLED;
            case 6 -> SourcePublicationStatus.ON_HIATUS;
            default -> SourcePublicationStatus.UNKNOWN;
        };
    }

    private static URI requireLoopback(URI value) {
        URI uri = Preconditions.requireNonNull(value, "baseUri").normalize();
        String host = uri.getHost();
        if (!"http".equalsIgnoreCase(uri.getScheme())
                || host == null
                || !(host.equals("127.0.0.1") || host.equals("::1"))
                || uri.getPort() < 1
                || uri.getUserInfo() != null
                || uri.getQuery() != null
                || uri.getFragment() != null) {
            throw new IllegalArgumentException("Extension engine must use an explicit HTTP loopback address and port");
        }
        return URI.create("http://" + (host.contains(":") ? "[" + host + "]" : host) + ":" + uri.getPort() + "/");
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static String jsonString(String value) {
        StringBuilder result = new StringBuilder("\"");
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '\"' -> result.append("\\\"");
                case '\\' -> result.append("\\\\");
                case '\b' -> result.append("\\b");
                case '\f' -> result.append("\\f");
                case '\n' -> result.append("\\n");
                case '\r' -> result.append("\\r");
                case '\t' -> result.append("\\t");
                default -> {
                    if (character < 0x20) {
                        result.append(String.format(Locale.ROOT, "\\u%04x", (int) character));
                    } else {
                        result.append(character);
                    }
                }
            }
        }
        return result.append('\"').toString();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> object(Object value) {
        if (value instanceof Map<?, ?> map && map.keySet().stream().allMatch(String.class::isInstance)) {
            return (Map<String, Object>) map;
        }
        throw new IllegalStateException("Extension engine JSON value must be an object");
    }

    @SuppressWarnings("unchecked")
    private static List<Object> array(Map<String, Object> value, String name) {
        Object member = value.get(name);
        if (member instanceof List<?> list) {
            return (List<Object>) list;
        }
        throw new IllegalStateException("Extension engine JSON member " + name + " must be an array");
    }

    private static String text(Map<String, Object> value, String name) {
        return optionalText(value, name)
                .orElseThrow(() -> new IllegalStateException("Extension engine JSON member " + name + " is missing"));
    }

    private static Optional<String> optionalText(Map<String, Object> value, String name) {
        Object member = value.get(name);
        return member instanceof String text && !text.isBlank() ? Optional.of(text) : Optional.empty();
    }

    private static boolean booleanValue(Object value) {
        return value instanceof Boolean result && result;
    }

    private static long longValue(Object value) {
        return value instanceof Number number ? number.longValue() : 0L;
    }

    private static double doubleValue(Object value, double fallback) {
        if (value instanceof Number number && Double.isFinite(number.doubleValue())) {
            return number.doubleValue();
        }
        return fallback;
    }

    private static Optional<URI> webUri(Object value) {
        if (!(value instanceof String text) || text.isBlank()) {
            return Optional.empty();
        }
        try {
            URI uri = URI.create(text);
            String scheme = uri.getScheme();
            return uri.isAbsolute() && ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
                    ? Optional.of(uri)
                    : Optional.empty();
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }

    private static URI requiredWebUri(Object value, String label) {
        return webUri(value).orElseThrow(() -> new IllegalStateException(label + " must be an absolute HTTP(S) URI"));
    }

    private static Optional<Instant> instant(Object value) {
        long millis = longValue(value);
        return millis <= 0 ? Optional.empty() : Optional.of(Instant.ofEpochMilli(millis));
    }

    private static SourceStreamFormat format(URI uri) {
        String path = Optional.ofNullable(uri.getPath()).orElse("").toLowerCase(Locale.ROOT);
        if (path.endsWith(".m3u8")) {
            return SourceStreamFormat.HLS;
        }
        if (path.endsWith(".mpd")) {
            return SourceStreamFormat.DASH;
        }
        return SourceStreamFormat.AUTOMATIC;
    }

    private static Map<String, String> stringMap(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return Map.of();
        }
        Map<String, String> result = new LinkedHashMap<>();
        map.forEach((key, item) -> {
            if (key instanceof String name && !name.isBlank() && item instanceof String text) {
                result.put(name, text);
            }
        });
        return Map.copyOf(result);
    }

    private static Map<String, String> modelParameters(RemoteSource source, SourceCatalogueItemId itemId) {
        Map<String, String> parameters = new LinkedHashMap<>();
        parameters.put("url", itemId.value());
        CatalogueModel model = source.models.get(itemId);
        if (model != null) {
            if (!model.title.isBlank()) parameters.put("title", model.title);
            if (!model.description.isBlank()) parameters.put("description", model.description);
            if (!model.thumbnailUrl.isBlank()) parameters.put("thumbnailUrl", model.thumbnailUrl);
            if (!model.artist.isBlank()) parameters.put("artist", model.artist);
            if (!model.author.isBlank()) parameters.put("author", model.author);
            if (!model.genre.isBlank()) parameters.put("genre", model.genre);
            if (model.status >= 0) parameters.put("status", Integer.toString(model.status));
        }
        return Map.copyOf(parameters);
    }

    private Optional<String> originalImage(RemoteSource source, Optional<URI> image) {
        if (image.isEmpty()) return Optional.empty();
        URI location = image.orElseThrow();
        if (location.getHost() == null
                || !location.getHost().equalsIgnoreCase(baseUri.getHost())
                || location.getPort() != baseUri.getPort()) {
            return Optional.of(location.toASCIIString());
        }
        Map<String, String> query = query(location.getRawQuery());
        return source.remoteId.equals(query.get("sourceId")) ? Optional.ofNullable(query.get("url")) : Optional.empty();
    }

    private static Map<String, String> query(String value) {
        if (value == null || value.isBlank()) return Map.of();
        Map<String, String> result = new LinkedHashMap<>();
        for (String pair : value.split("&")) {
            String[] parts = pair.split("=", 2);
            if (parts.length == 2) {
                result.put(
                        java.net.URLDecoder.decode(parts[0], StandardCharsets.UTF_8),
                        java.net.URLDecoder.decode(parts[1], StandardCharsets.UTF_8));
            }
        }
        return Map.copyOf(result);
    }

    private record RemoteSource(
            String remoteId,
            SourceDescriptor descriptor,
            Optional<URI> homePage,
            Map<SourceCatalogueItemId, CatalogueModel> models) {
    }

    private record CatalogueModel(
            String title,
            String description,
            String thumbnailUrl,
            String artist,
            String author,
            String genre,
            int status) {
    }

    private static <K, V> Map<K, V> retainedModels() {
        return java.util.Collections.synchronizedMap(new LinkedHashMap<>(64, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
                return size() > MAX_RETAINED_MODELS;
            }
        });
    }

    private final class MangaBridge implements CatalogueSource, DetailedSource, PagedSource, WebSource {
        private final RemoteSource source;

        private MangaBridge(RemoteSource source) {
            this.source = source;
        }

        @Override
        public SourceDescriptor descriptor() {
            return source.descriptor;
        }

        @Override
        public URI homePage() {
            return DesktopExtensionSourceBridge.this.homePage(source)
                    .orElseThrow(() -> new IllegalStateException("Desktop APK source has no discoverable web URL"));
        }

        @Override
        public Optional<URI> titlePage(SourceCatalogueItemId itemId) {
            return DesktopExtensionSourceBridge.this.titlePage(source, itemId);
        }

        @Override
        public SourcePage popular(SourceBrowseRequest request) {
            applyPreferences(source, request.preferences());
            return catalogue(source, "popular", request.page(), null);
        }

        @Override
        public boolean supportsLatest() {
            return true;
        }

        @Override
        public SourcePage latest(SourceBrowseRequest request) {
            applyPreferences(source, request.preferences());
            return catalogue(source, "latest", request.page(), null);
        }

        @Override
        public SourcePage search(SourceSearchRequest request) {
            applyPreferences(source, request.browseRequest().preferences());
            return catalogue(source, "search", request.browseRequest().page(), request.query());
        }

        @Override
        public List<SourcePreferenceDefinition> preferences() {
            return DesktopExtensionSourceBridge.this.preferences(source);
        }

        @Override
        public SourceTitleDetails details(SourceCatalogueItemId itemId) {
            CatalogueModel model = source.models.get(itemId);
            SourceCatalogueItem item = new SourceCatalogueItem(
                    itemId,
                    model == null || model.title.isBlank() ? itemId.value() : model.title,
                    model == null ? "" : model.description,
                    model == null ? Optional.empty() : webUri(model.thumbnailUrl),
                    SourceContentKind.MANGA);
            return DesktopExtensionSourceBridge.this.details(source, item);
        }

        @Override
        public SourceTitleDetails details(SourceCatalogueItem item) {
            requireOwned(source, item.id());
            return DesktopExtensionSourceBridge.this.details(source, item);
        }

        @Override
        public List<SourceContentUnit> contentUnits(SourceCatalogueItemId itemId) {
            requireOwned(source, itemId);
            Map<String, Object> document = object(get(
                    "/api/v1/manga/" + encode(source.remoteId) + "/chapters",
                    modelParameters(source, itemId)));
            List<SourceContentUnit> result = new ArrayList<>();
            for (Object value : array(document, "chapters")) {
                Map<String, Object> chapter = object(value);
                SourceContentUnitId id = new SourceContentUnitId(itemId, text(chapter, "url"));
                result.add(new SourceContentUnit(id, text(chapter, "name"), instant(chapter.get("date_upload"))));
            }
            return List.copyOf(result);
        }

        @Override
        public List<SourcePageResource> pages(SourceContentUnitId contentUnitId) {
            requireOwned(source, contentUnitId.itemId());
            Map<String, Object> document = object(get(
                    "/api/v1/manga/" + encode(source.remoteId) + "/pages",
                    Map.of("url", contentUnitId.value())));
            List<SourcePageResource> result = new ArrayList<>();
            int fallbackIndex = 0;
            for (Object value : array(document, "pages")) {
                Map<String, Object> page = object(value);
                String location = optionalText(page, "imageUrl").or(() -> optionalText(page, "url"))
                        .orElseThrow(() -> new IllegalStateException("Manga page has no location"));
                int index = page.get("index") instanceof Number number ? number.intValue() : fallbackIndex;
                result.add(new SourcePageResource(
                        contentUnitId,
                        location,
                        Math.max(index, 0),
                        SourcePageResource.UNKNOWN_SIZE));
                fallbackIndex++;
            }
            return List.copyOf(result);
        }

        @Override
        public byte[] readPage(SourcePageResource page) {
            requireOwned(source, page.contentUnitId().itemId());
            return bytes("/api/v1/proxy", Map.of(
                    "sourceId", source.remoteId,
                    "url", page.value(),
                    "pageIndex", Integer.toString(page.index())));
        }
    }

    private final class AnimeBridge implements CatalogueSource, DetailedSource, StreamingSource, WebSource {
        private final RemoteSource source;

        private AnimeBridge(RemoteSource source) {
            this.source = source;
        }

        @Override
        public SourceDescriptor descriptor() {
            return source.descriptor;
        }

        @Override
        public URI homePage() {
            return DesktopExtensionSourceBridge.this.homePage(source)
                    .orElseThrow(() -> new IllegalStateException("Desktop APK source has no discoverable web URL"));
        }

        @Override
        public Optional<URI> titlePage(SourceCatalogueItemId itemId) {
            return DesktopExtensionSourceBridge.this.titlePage(source, itemId);
        }

        @Override
        public SourcePage popular(SourceBrowseRequest request) {
            applyPreferences(source, request.preferences());
            return catalogue(source, "popular", request.page(), null);
        }

        @Override
        public boolean supportsLatest() {
            return true;
        }

        @Override
        public SourcePage latest(SourceBrowseRequest request) {
            applyPreferences(source, request.preferences());
            return catalogue(source, "latest", request.page(), null);
        }

        @Override
        public SourcePage search(SourceSearchRequest request) {
            applyPreferences(source, request.browseRequest().preferences());
            return catalogue(source, "search", request.browseRequest().page(), request.query());
        }

        @Override
        public List<SourcePreferenceDefinition> preferences() {
            return DesktopExtensionSourceBridge.this.preferences(source);
        }

        @Override
        public SourceTitleDetails details(SourceCatalogueItemId itemId) {
            CatalogueModel model = source.models.get(itemId);
            SourceCatalogueItem item = new SourceCatalogueItem(
                    itemId,
                    model == null || model.title.isBlank() ? itemId.value() : model.title,
                    model == null ? "" : model.description,
                    model == null ? Optional.empty() : webUri(model.thumbnailUrl),
                    SourceContentKind.ANIME);
            return DesktopExtensionSourceBridge.this.details(source, item);
        }

        @Override
        public SourceTitleDetails details(SourceCatalogueItem item) {
            requireOwned(source, item.id());
            return DesktopExtensionSourceBridge.this.details(source, item);
        }

        @Override
        public List<SourceEpisode> episodes(SourceCatalogueItemId itemId) {
            requireOwned(source, itemId);
            Map<String, Object> document = object(get(
                    "/api/v1/anime/" + encode(source.remoteId) + "/episodes",
                    modelParameters(source, itemId)));
            List<SourceEpisode> result = new ArrayList<>();
            for (Object value : array(document, "episodes")) {
                Map<String, Object> episode = object(value);
                SourceEpisodeId id = new SourceEpisodeId(itemId, text(episode, "url"));
                result.add(new SourceEpisode(
                        id,
                        text(episode, "name"),
                        doubleValue(episode.get("episode_number"), SourceEpisode.UNKNOWN_NUMBER),
                        instant(episode.get("date_upload")),
                        optionalText(episode, "scanlator"),
                        webUri(episode.get("preview_url"))));
            }
            return List.copyOf(result);
        }

        @Override
        public List<SourceVideoStream> streams(SourceEpisodeId episodeId) {
            requireOwned(source, episodeId.itemId());
            Map<String, Object> document = object(get(
                    "/api/v1/anime/" + encode(source.remoteId) + "/videos",
                    Map.of("url", episodeId.value())));
            List<SourceVideoStream> result = new ArrayList<>();
            int index = 0;
            for (Object value : array(document, "videos")) {
                Map<String, Object> video = object(value);
                URI original = requiredWebUri(video.get("videoUrl"), "Anime stream location");
                Map<String, String> headers = stringMap(video.get("headers"));
                SourceStreamFormat streamFormat = format(original);
                List<SourceSubtitleTrack> subtitles = subtitleTracks(video, headers, index);
                String quality = optionalText(video, "videoTitle")
                        .or(() -> optionalText(video, "resolution"))
                        .orElse("Stream " + (index + 1));
                result.add(new SourceVideoStream(
                        "stream-" + index,
                        quality,
                        original,
                        streamFormat,
                        headers,
                        subtitles));
                index++;
            }
            return List.copyOf(result);
        }
    }

    private List<SourceSubtitleTrack> subtitleTracks(
            Map<String, Object> video,
            Map<String, String> headers,
            int streamIndex) {
        Object raw = video.get("subtitleTracks");
        if (!(raw instanceof List<?> tracks)) {
            return List.of();
        }
        List<SourceSubtitleTrack> result = new ArrayList<>();
        for (int index = 0; index < tracks.size(); index++) {
            Map<String, Object> track = object(tracks.get(index));
            URI original = requiredWebUri(track.get("url"), "Subtitle location");
            String language = optionalText(track, "lang").orElse("Subtitle " + (index + 1));
            result.add(new SourceSubtitleTrack(
                    "subtitle-" + streamIndex + "-" + index,
                    language,
                    optionalText(track, "lang"),
                    original,
                    headers));
        }
        return List.copyOf(result);
    }

    private static void requireOwned(RemoteSource source, SourceCatalogueItemId itemId) {
        Preconditions.requireNonNull(itemId, "itemId");
        if (!source.descriptor.id().equals(itemId.sourceId())) {
            throw new IllegalArgumentException("Catalogue item belongs to a different source");
        }
    }
}
