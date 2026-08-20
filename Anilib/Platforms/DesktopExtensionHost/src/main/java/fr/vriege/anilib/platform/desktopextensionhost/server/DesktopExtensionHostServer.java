package fr.vriege.anilib.platform.desktopextensionhost.server;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import fr.vriege.anilib.platform.desktopextensionhost.extension.ExtensionDownloadClient;
import fr.vriege.anilib.platform.desktopextensionhost.extension.ExtensionKind;
import fr.vriege.anilib.platform.desktopextensionhost.extension.ExtensionOperationException;
import fr.vriege.anilib.platform.desktopextensionhost.extension.ExtensionRegistry;
import fr.vriege.anilib.platform.desktopextensionhost.extension.ExtensionRuntimeCatalog;
import fr.vriege.anilib.platform.desktopextensionhost.extension.ExtensionSourceOperations;
import fr.vriege.anilib.platform.desktopextensionhost.extension.InstalledExtension;
import fr.vriege.anilib.platform.desktopextensionhost.extension.LoadedSource;
import fr.vriege.anilib.platform.desktopextensionhost.protocol.DesktopExtensionHostProtocol;
import fr.vriege.anilib.platform.desktopextensionhost.compat.aniyomi.animesource.model.AnimesPage;
import fr.vriege.anilib.platform.desktopextensionhost.compat.aniyomi.animesource.model.SAnime;
import fr.vriege.anilib.platform.desktopextensionhost.compat.aniyomi.animesource.model.SEpisode;
import fr.vriege.anilib.platform.desktopextensionhost.compat.aniyomi.animesource.model.Track;
import fr.vriege.anilib.platform.desktopextensionhost.compat.aniyomi.animesource.model.Video;
import fr.vriege.anilib.platform.desktopextensionhost.compat.aniyomi.source.model.MangasPage;
import fr.vriege.anilib.platform.desktopextensionhost.compat.aniyomi.source.model.Page;
import fr.vriege.anilib.platform.desktopextensionhost.compat.aniyomi.source.model.SChapter;
import fr.vriege.anilib.platform.desktopextensionhost.compat.aniyomi.source.model.SManga;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Objects;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class DesktopExtensionHostServer implements AutoCloseable {
    private static final System.Logger LOGGER = System.getLogger(DesktopExtensionHostServer.class.getName());
    private static final Pattern WEB_LOCATION = Pattern.compile("https?://[^\\s]+", Pattern.CASE_INSENSITIVE);
    private static final Pattern NAMED_SECRET = Pattern.compile(
            "(?i)(authorization|token|api[_-]?key|cookie)\\s*[:=]\\s*[^\\s,;]+"
    );
    private static final Pattern BEARER_SECRET = Pattern.compile("(?i)bearer\\s+[^\\s,;]+");
    private static final String CORRELATION_HEADER = "X-Anilib-Correlation-Id";
    private final Path dataDirectory;
    private final HttpServer server;
    private final ExecutorService executor;
    private final ExtensionRegistry extensionRegistry;
    private final ExtensionRuntimeCatalog runtimeCatalog;
    private final ExtensionSourceOperations sourceOperations;
    private final ExtensionDownloadClient downloadClient;

    private DesktopExtensionHostServer(
            Path dataDirectory,
            HttpServer server,
            ExecutorService executor,
            ExtensionRegistry extensionRegistry,
            ExtensionRuntimeCatalog runtimeCatalog,
            ExtensionSourceOperations sourceOperations,
            ExtensionDownloadClient downloadClient) {
        this.dataDirectory = dataDirectory;
        this.server = server;
        this.executor = executor;
        this.extensionRegistry = extensionRegistry;
        this.runtimeCatalog = runtimeCatalog;
        this.sourceOperations = sourceOperations;
        this.downloadClient = downloadClient;
    }

    public static DesktopExtensionHostServer open(InetAddress address, int port, Path dataDirectory) {
        InetAddress bindAddress = Objects.requireNonNull(address, "address");
        if (!bindAddress.isLoopbackAddress()) {
            throw new IllegalArgumentException("Desktop engine must bind to a loopback address");
        }
        if (port < 0 || port > 65_535) {
            throw new IllegalArgumentException("port must be between 0 and 65535");
        }
        Path data = prepareDataDirectory(dataDirectory);
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(bindAddress, port), 0);
            ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
            ExtensionRegistry registry = new ExtensionRegistry(data);
            registry.prepareInstalledArchives();
            ExtensionRuntimeCatalog catalog = new ExtensionRuntimeCatalog(registry);
            DesktopExtensionHostServer result = new DesktopExtensionHostServer(
                    data,
                    server,
                    executor,
                    registry,
                    catalog,
                    new ExtensionSourceOperations(catalog),
                    new ExtensionDownloadClient());
            result.configure();
            server.setExecutor(executor);
            return result;
        } catch (IOException exception) {
            throw new UncheckedIOException("Unable to create desktop engine server", exception);
        }
    }

    private static Path prepareDataDirectory(Path value) {
        Path directory = Objects.requireNonNull(value, "dataDirectory").toAbsolutePath().normalize();
        try {
            if (Files.exists(directory, LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(directory)) {
                throw new IllegalArgumentException("Desktop engine data directory must not be a symbolic link");
            }
            Files.createDirectories(directory);
            if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
                throw new IllegalArgumentException("Desktop engine data path is not a directory");
            }
            return directory;
        } catch (IOException exception) {
            throw new UncheckedIOException("Unable to prepare desktop engine data directory", exception);
        }
    }

    private void configure() {
        server.createContext(DesktopExtensionHostProtocol.HEALTH_PATH, exchange -> safely(exchange, () -> {
            if (ExtensionHostHttpExchange.requireGet(exchange)) {
                ExtensionHostHttpExchange.json(exchange, 200, healthJson());
            }
        }));
        server.createContext(DesktopExtensionHostProtocol.CAPABILITIES_PATH, exchange -> safely(exchange, () -> {
            if (ExtensionHostHttpExchange.requireGet(exchange)) {
                ExtensionHostHttpExchange.json(exchange, 200, DesktopExtensionHostCapabilities.bootstrap().toJson());
            }
        }));
        server.createContext(DesktopExtensionHostProtocol.SOURCES_PATH, exchange -> safely(exchange, () -> {
            if (ExtensionHostHttpExchange.requireGet(exchange)) {
                try (ExtensionRuntimeCatalog.Snapshot snapshot = runtimeCatalog.discover()) {
                    ExtensionHostHttpExchange.json(exchange, 200, sourcesJson(snapshot));
                }
            }
        }));
        server.createContext(
                DesktopExtensionHostProtocol.INSTALLED_EXTENSIONS_PATH,
                exchange -> safely(exchange, () -> {
                    if (ExtensionHostHttpExchange.requireGet(exchange)) {
                        ExtensionHostHttpExchange.json(
                                exchange, 200, installedJson(extensionRegistry.installed()));
                    }
                }));
        server.createContext(DesktopExtensionHostProtocol.INSTALL_EXTENSION_PATH, exchange -> safely(exchange, () -> {
            if (!ExtensionHostHttpExchange.requirePost(exchange)) {
                return;
            }
            URI uri = URI.create(ExtensionHostHttpExchange.stringField(
                    ExtensionHostHttpExchange.body(exchange), "apk"));
            Path downloaded = downloadClient.download(uri, dataDirectory.resolve("downloads"));
            try {
                runtimeCatalog.reset();
                InstalledExtension installed = extensionRegistry.install(downloaded);
                ExtensionHostHttpExchange.json(exchange, 200, "{\"ok\":true,\"name\":"
                        + ExtensionHostHttpExchange.jsonString(installed.metadata().displayName())
                        + ",\"pkg\":"
                        + ExtensionHostHttpExchange.jsonString(installed.metadata().packageName()) + '}');
            } finally {
                deleteDownload(downloaded);
            }
        }));
        server.createContext(DesktopExtensionHostProtocol.UNINSTALL_EXTENSION_PATH, exchange -> safely(exchange, () -> {
            if (!ExtensionHostHttpExchange.requirePost(exchange)) {
                return;
            }
            String packageName = ExtensionHostHttpExchange.stringField(
                    ExtensionHostHttpExchange.body(exchange), "pkg");
            runtimeCatalog.reset();
            boolean removed = extensionRegistry.uninstall(packageName);
            ExtensionHostHttpExchange.json(exchange, removed ? 200 : 404,
                    removed ? "{\"ok\":true}" : "{\"ok\":false,\"error\":\"not_installed\"}");
        }));
        server.createContext(
                DesktopExtensionHostProtocol.EXTENSION_REPOSITORIES_PATH,
                exchange -> safely(exchange, () -> {
                    if (ExtensionHostHttpExchange.requirePost(exchange)) {
                        ExtensionHostHttpExchange.json(exchange, 200, "{\"ok\":true}");
                    }
                }));
        server.createContext(DesktopExtensionHostProtocol.MANGA_PATH,
                exchange -> safely(exchange, () -> handleManga(exchange)));
        server.createContext(DesktopExtensionHostProtocol.ANIME_PATH,
                exchange -> safely(exchange, () -> handleAnime(exchange)));
        server.createContext(DesktopExtensionHostProtocol.SOURCE_PATH,
                exchange -> safely(exchange, () -> handleSource(exchange)));
        server.createContext(DesktopExtensionHostProtocol.PROXY_PATH,
                exchange -> safely(exchange, () -> handleProxy(exchange)));
    }

    private void handleSource(HttpExchange exchange) {
        String path = exchange.getRequestURI().getPath();
        String[] segments = path.substring(DesktopExtensionHostProtocol.SOURCE_PATH.length()).split("/", -1);
        if (segments.length != 2 || !"prefs".equals(segments[1])) {
            throw new IllegalArgumentException("Invalid source operation path");
        }
        parseSourceId(segments[0]);
        if ("GET".equals(exchange.getRequestMethod())) {
            ExtensionHostHttpExchange.json(exchange, 200, "{\"prefs\":[]}");
        } else if ("POST".equals(exchange.getRequestMethod())) {
            ExtensionHostHttpExchange.body(exchange);
            ExtensionHostHttpExchange.json(exchange, 200, "{\"ok\":true}");
        } else {
            exchange.getResponseHeaders().set("Allow", "GET, POST");
            ExtensionHostHttpExchange.json(exchange, 405, "{\"error\":\"method_not_allowed\"}");
        }
    }

    private void handleProxy(HttpExchange exchange) {
        if (!ExtensionHostHttpExchange.requireGet(exchange)) {
            return;
        }
        Map<String, String> query = ExtensionHostHttpExchange.query(exchange);
        long sourceId = parseSourceId(required(query, "sourceId"));
        int pageIndex = query.containsKey("pageIndex")
                ? nonNegativeInteger(query.get("pageIndex"), "pageIndex")
                : -1;
        ExtensionSourceOperations.ProxiedResource resource =
                sourceOperations.proxy(sourceId, required(query, "url"), pageIndex);
        ExtensionHostHttpExchange.bytes(exchange, 200, resource.contentType(), resource.body());
    }

    private static int nonNegativeInteger(String value, String name) {
        try {
            int parsed = Integer.parseInt(value);
            if (parsed < 0) throw new IllegalArgumentException(name + " must not be negative");
            return parsed;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(name + " must be an integer", exception);
        }
    }

    private void handleManga(HttpExchange exchange) {
        if (!ExtensionHostHttpExchange.requireGet(exchange)) {
            return;
        }
        Route route = sourceRoute(exchange, DesktopExtensionHostProtocol.MANGA_PATH);
        Map<String, String> query = ExtensionHostHttpExchange.query(exchange);
        String json = switch (route.operation()) {
            case "popular", "latest", "search" -> mangaPageJson(sourceOperations.mangaCatalogue(
                    route.sourceId(), route.operation(), page(query), query.get("query")));
            case "details" -> mangaJson(sourceOperations.mangaDetails(route.sourceId(), sourceModel(query)));
            case "chapters" -> chaptersJson(sourceOperations.chapters(route.sourceId(), sourceModel(query)));
            case "pages" -> pagesJson(sourceOperations.pages(route.sourceId(), required(query, "url")));
            default -> throw new IllegalArgumentException("Unknown manga operation");
        };
        ExtensionHostHttpExchange.json(exchange, 200, json);
    }

    private void handleAnime(HttpExchange exchange) {
        if (!ExtensionHostHttpExchange.requireGet(exchange)) {
            return;
        }
        Route route = sourceRoute(exchange, DesktopExtensionHostProtocol.ANIME_PATH);
        Map<String, String> query = ExtensionHostHttpExchange.query(exchange);
        String json = switch (route.operation()) {
            case "popular", "latest", "search" -> animePageJson(sourceOperations.animeCatalogue(
                    route.sourceId(), route.operation(), page(query), query.get("query")));
            case "details" -> animeJson(sourceOperations.animeDetails(route.sourceId(), sourceModel(query)));
            case "episodes" -> episodesJson(sourceOperations.episodes(route.sourceId(), sourceModel(query)));
            case "videos" -> videosJson(sourceOperations.videos(route.sourceId(), required(query, "url")));
            default -> throw new IllegalArgumentException("Unknown anime operation");
        };
        ExtensionHostHttpExchange.json(exchange, 200, json);
    }

    private static Route sourceRoute(HttpExchange exchange, String prefix) {
        String path = exchange.getRequestURI().getPath();
        String[] segments = path.substring(prefix.length()).split("/", -1);
        if (segments.length != 2 || segments[0].isBlank() || segments[1].isBlank()) {
            throw new IllegalArgumentException("Invalid source operation path");
        }
        return new Route(parseSourceId(segments[0]), segments[1]);
    }

    private static long parseSourceId(String value) {
        try {
            return Long.parseUnsignedLong(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Source id is not an unsigned 64-bit integer", exception);
        }
    }

    private static int page(Map<String, String> query) {
        try {
            int value = Integer.parseInt(query.getOrDefault("page", "1"));
            if (value < 1) throw new NumberFormatException();
            return value;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("page must be a positive integer", exception);
        }
    }

    private static String required(Map<String, String> query, String name) {
        String value = query.get(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing query parameter: " + name);
        }
        return value;
    }

    private static ExtensionSourceOperations.SourceModel sourceModel(Map<String, String> query) {
        return new ExtensionSourceOperations.SourceModel(
                required(query, "url"),
                query.get("title"),
                query.get("thumbnailUrl"),
                query.get("description"),
                query.get("artist"),
                query.get("author"),
                query.get("genre"),
                integer(query.get("status"), -1));
    }

    private static int integer(String value, int fallback) {
        try {
            return value == null ? fallback : Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static String mangaPageJson(MangasPage page) {
        return "{\"mangas\":[" + String.join(",", page.getMangas().stream()
                .map(DesktopExtensionHostServer::mangaValue).toList())
                + "],\"hasNextPage\":" + page.getHasNextPage() + '}';
    }

    private static String animePageJson(AnimesPage page) {
        return "{\"animes\":[" + String.join(",", page.getAnimes().stream()
                .map(DesktopExtensionHostServer::animeValue).toList())
                + "],\"hasNextPage\":" + page.getHasNextPage() + '}';
    }

    private static String mangaJson(SManga manga) { return "{\"manga\":" + mangaValue(manga) + '}'; }
    private static String animeJson(SAnime anime) { return "{\"anime\":" + animeValue(anime) + '}'; }

    private static String mangaValue(SManga manga) {
        return "{\"url\":" + json(manga.getUrl())
                + ",\"title\":" + json(manga.getTitle())
                + ",\"artist\":" + json(manga.getArtist())
                + ",\"author\":" + json(manga.getAuthor())
                + ",\"description\":" + json(manga.getDescription())
                + ",\"genre\":" + json(manga.getGenre())
                + ",\"status\":" + manga.getStatus()
                + ",\"thumbnail_url\":" + json(manga.getThumbnail_url()) + '}';
    }

    private static String animeValue(SAnime anime) {
        return "{\"url\":" + json(anime.getUrl())
                + ",\"title\":" + json(anime.getTitle())
                + ",\"artist\":" + json(anime.getArtist())
                + ",\"author\":" + json(anime.getAuthor())
                + ",\"description\":" + json(anime.getDescription())
                + ",\"genre\":" + json(anime.getGenre())
                + ",\"status\":" + anime.getStatus()
                + ",\"thumbnail_url\":" + json(anime.getThumbnail_url())
                + ",\"background_url\":" + json(anime.getBackground_url()) + '}';
    }

    private static String chaptersJson(List<SChapter> chapters) {
        return "{\"chapters\":[" + String.join(",", chapters.stream().map(chapter ->
                "{\"url\":" + json(chapter.getUrl())
                        + ",\"name\":" + json(chapter.getName())
                        + ",\"date_upload\":" + chapter.getDate_upload()
                        + ",\"chapter_number\":" + chapter.getChapter_number()
                        + ",\"scanlator\":" + json(chapter.getScanlator()) + '}'
        ).toList()) + "]}";
    }

    private static String pagesJson(List<Page> pages) {
        return "{\"pages\":[" + String.join(",", pages.stream().map(page ->
                "{\"index\":" + page.getIndex()
                        + ",\"url\":" + json(page.getUrl())
                        + ",\"imageUrl\":" + json(page.getImageUrl()) + '}'
        ).toList()) + "]}";
    }

    private static String episodesJson(List<SEpisode> episodes) {
        return "{\"episodes\":[" + String.join(",", episodes.stream().map(episode ->
                "{\"url\":" + json(episode.getUrl())
                        + ",\"name\":" + json(episode.getName())
                        + ",\"episode_number\":" + episode.getEpisode_number()
                        + ",\"date_upload\":" + episode.getDate_upload()
                        + ",\"scanlator\":" + json(episode.getScanlator())
                        + ",\"preview_url\":" + json(episode.getPreview_url()) + '}'
        ).toList()) + "]}";
    }

    private static String videosJson(List<Video> videos) {
        return "{\"videos\":[" + String.join(",", videos.stream().map(video ->
                "{\"videoUrl\":" + json(video.getVideoUrl())
                        + ",\"videoTitle\":" + json(video.getVideoTitle())
                        + ",\"resolution\":" + number(video.getResolution())
                        + ",\"headers\":" + headers(video)
                        + ",\"subtitleTracks\":[" + String.join(",", video.getSubtitleTracks().stream()
                        .map(DesktopExtensionHostServer::trackJson).toList()) + "]}"
        ).toList()) + "]}";
    }

    private static String headers(Video video) {
        if (video.getHeaders() == null) return "{}";
        List<String> values = new java.util.ArrayList<>();
        for (int index = 0; index < video.getHeaders().size(); index++) {
            values.add(json(video.getHeaders().name(index)) + ':' + json(video.getHeaders().value(index)));
        }
        return '{' + String.join(",", values) + '}';
    }

    private static String trackJson(Track track) {
        return "{\"url\":" + json(track.url()) + ",\"lang\":" + json(track.lang()) + '}';
    }

    private static String json(String value) {
        return value == null ? "null" : ExtensionHostHttpExchange.jsonString(value);
    }

    private static String number(Number value) { return value == null ? "null" : value.toString(); }

    private record Route(long sourceId, String operation) {
    }

    public void start() {
        server.start();
    }

    public int port() {
        return server.getAddress().getPort();
    }

    public Path dataDirectory() {
        return dataDirectory;
    }

    private static String healthJson() {
        return "{\"status\":\"ok\",\"service\":\"" + DesktopExtensionHostProtocol.SERVICE
                + "\",\"protocolVersion\":" + DesktopExtensionHostProtocol.VERSION + '}';
    }

    private static String installedJson(List<InstalledExtension> extensions) {
        return "{\"extensions\":[" + String.join(",", extensions.stream().map(extension -> {
            var metadata = extension.metadata();
            return "{\"pkg\":" + ExtensionHostHttpExchange.jsonString(metadata.packageName())
                    + ",\"name\":" + ExtensionHostHttpExchange.jsonString(metadata.displayName())
                    + ",\"versionName\":" + ExtensionHostHttpExchange.jsonString(metadata.versionName())
                    + ",\"versionCode\":" + metadata.versionCode()
                    + ",\"kind\":"
                    + ExtensionHostHttpExchange.jsonString(metadata.kind().name().toLowerCase(Locale.ROOT))
                    + ",\"nsfw\":" + metadata.adult() + '}';
        }).toList()) + "]}";
    }

    private static String sourcesJson(ExtensionRuntimeCatalog.Snapshot snapshot) {
        List<String> manga = new java.util.ArrayList<>();
        List<String> anime = new java.util.ArrayList<>();
        for (LoadedSource source : snapshot.sources()) {
            String value = "{\"id\":" + ExtensionHostHttpExchange.jsonString(Long.toUnsignedString(source.id()))
                    + ",\"name\":" + ExtensionHostHttpExchange.jsonString(source.name())
                    + ",\"lang\":" + ExtensionHostHttpExchange.jsonString(source.language())
                    + ",\"pkg\":" + ExtensionHostHttpExchange.jsonString(source.packageName())
                    + ",\"baseUrl\":" + json(sourceBaseUrl(source.instance())) + '}';
            (source.kind() == ExtensionKind.MANGA ? manga : anime).add(value);
        }
        String failures = String.join(",", snapshot.failures().entrySet().stream()
                .map(entry -> ExtensionHostHttpExchange.jsonString(entry.getKey()) + ':'
                        + ExtensionHostHttpExchange.jsonString(entry.getValue()))
                .toList());
        return "{\"manga\":[" + String.join(",", manga)
                + "],\"anime\":[" + String.join(",", anime)
                + "],\"failures\":{" + failures + "}}";
    }

    private static String sourceBaseUrl(Object source) {
        for (String method : List.of("getHomeUrl", "getBaseUrl")) {
            try {
                Object value = source.getClass().getMethod(method).invoke(source);
                if (value instanceof String url && !url.isBlank()) {
                    return url;
                }
            } catch (ReflectiveOperationException ignored) {
            }
        }
        return null;
    }

    private static void safely(HttpExchange exchange, Runnable action) {
        String correlationId = UUID.randomUUID().toString();
        exchange.getResponseHeaders().set(CORRELATION_HEADER, correlationId);
        try {
            action.run();
        } catch (ExtensionOperationException exception) {
            logFailure(correlationId, exchange, exception);
            ExtensionHostHttpExchange.json(exchange, exception.code().statusCode(), errorJson(
                    exception.code().protocolValue(), exception.getMessage(), correlationId));
        } catch (IllegalArgumentException exception) {
            logFailure(correlationId, exchange, exception);
            ExtensionHostHttpExchange.json(exchange, 400, errorJson(
                    "invalid_request", "The extension host rejected the request", correlationId));
        } catch (RuntimeException | LinkageError exception) {
            logFailure(correlationId, exchange, exception);
            ExtensionHostHttpExchange.json(exchange, 500, errorJson(
                    "internal_host_failure", "The desktop extension host could not complete the request",
                    correlationId));
        }
    }

    private static String errorJson(String code, String message, String correlationId) {
        return "{\"error\":{\"code\":" + ExtensionHostHttpExchange.jsonString(code)
                + ",\"message\":" + ExtensionHostHttpExchange.jsonString(message)
                + ",\"correlationId\":" + ExtensionHostHttpExchange.jsonString(correlationId) + "}}";
    }

    private static void logFailure(String correlationId, HttpExchange exchange, Throwable failure) {
        StringBuilder diagnostic = new StringBuilder("Desktop extension host operation failed")
                .append(" correlationId=").append(correlationId)
                .append(" method=").append(exchange.getRequestMethod())
                .append(" path=").append(exchange.getRequestURI().getPath());
        if (failure instanceof ExtensionOperationException operation) {
            diagnostic.append(" operation=").append(operation.operation())
                    .append(" package=").append(operation.packageName())
                    .append(" sourceId=").append(Long.toUnsignedString(operation.sourceId()))
                    .append(" sourceUrl=").append(sanitizeUrl(operation.sourceUrl()))
                    .append(" code=").append(operation.code().protocolValue());
        }
        int depth = 0;
        for (Throwable current = failure; current != null && depth < 12; current = current.getCause()) {
            diagnostic.append(System.lineSeparator())
                    .append("causedBy[").append(depth).append("]=")
                    .append(current.getClass().getName()).append(": ")
                    .append(sanitizeMessage(current.getMessage()));
            for (StackTraceElement frame : current.getStackTrace()) {
                diagnostic.append(System.lineSeparator()).append("  at ").append(frame);
            }
            if (current.getCause() == current) break;
            depth++;
        }
        LOGGER.log(System.Logger.Level.ERROR, diagnostic.toString());
    }

    private static String sanitizeUrl(String value) {
        if (value == null || value.isBlank()) return "";
        try {
            URI uri = URI.create(value);
            if (!uri.isAbsolute()) return uri.getPath() == null ? "" : uri.getPath();
            return new URI(uri.getScheme(), null, uri.getHost(), uri.getPort(), uri.getPath(), null, null).toString();
        } catch (java.net.URISyntaxException | IllegalArgumentException ignored) {
            return "<invalid-url>";
        }
    }

    private static String sanitizeMessage(String value) {
        if (value == null || value.isBlank()) return "<no-message>";
        Matcher matcher = WEB_LOCATION.matcher(value);
        StringBuilder sanitized = new StringBuilder();
        while (matcher.find()) {
            matcher.appendReplacement(sanitized, Matcher.quoteReplacement(sanitizeUrl(matcher.group())));
        }
        matcher.appendTail(sanitized);
        String withoutNamedSecrets = NAMED_SECRET.matcher(sanitized).replaceAll("$1=<redacted>");
        return BEARER_SECRET.matcher(withoutNamedSecrets).replaceAll("Bearer <redacted>");
    }

    private static void deleteDownload(Path downloaded) {
        try {
            Files.deleteIfExists(downloaded);
        } catch (IOException exception) {
            System.err.println("Unable to remove temporary extension download: " + exception.getMessage());
        }
    }

    @Override
    public void close() {
        server.stop(0);
        executor.close();
        runtimeCatalog.close();
    }
}
