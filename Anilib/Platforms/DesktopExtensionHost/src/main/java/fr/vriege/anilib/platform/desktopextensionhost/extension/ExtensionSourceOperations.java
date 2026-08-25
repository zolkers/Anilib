package fr.vriege.anilib.platform.desktopextensionhost.extension;

import fr.vriege.anilib.framework.concurrent.runtime.LatestTaskPipeline;
import fr.vriege.anilib.framework.concurrent.runtime.TaskPipelineException;
import fr.vriege.anilib.platform.desktopextensionhost.compat.aniyomi.animesource.model.AnimesPage;
import fr.vriege.anilib.platform.desktopextensionhost.compat.aniyomi.animesource.model.Hoster;
import fr.vriege.anilib.platform.desktopextensionhost.compat.aniyomi.animesource.model.SAnime;
import fr.vriege.anilib.platform.desktopextensionhost.compat.aniyomi.animesource.model.SEpisode;
import fr.vriege.anilib.platform.desktopextensionhost.compat.aniyomi.animesource.model.Video;
import fr.vriege.anilib.platform.desktopextensionhost.compat.aniyomi.source.model.MangasPage;
import fr.vriege.anilib.platform.desktopextensionhost.compat.aniyomi.source.model.Page;
import fr.vriege.anilib.platform.desktopextensionhost.compat.aniyomi.source.model.SChapter;
import fr.vriege.anilib.platform.desktopextensionhost.compat.aniyomi.source.model.SManga;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.concurrent.atomic.AtomicLong;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import java.io.InputStream;

public final class ExtensionSourceOperations implements AutoCloseable {
    private static final int MAX_PROXY_BYTES = 64 * 1024 * 1024;
    private static final int MAX_RETAINED_MODELS = 4_096;
    private static final Pattern WEB_HOST = Pattern.compile("https?://([^/\\\\\"']+)", Pattern.CASE_INSENSITIVE);
    private static final System.Logger LOGGER = System.getLogger(ExtensionSourceOperations.class.getName());
    private static final Duration OPERATION_TIMEOUT = Duration.ofSeconds(60);
    private static final int OPERATION_PARALLELISM = Math.max(
            2,
            Math.min(8, Runtime.getRuntime().availableProcessors()));

    private final ExtensionRuntimeCatalog catalog;
    private final LatestTaskPipeline<OperationKey> operationPipeline = new LatestTaskPipeline<>(
            "anilib-extension-operation",
            OPERATION_PARALLELISM,
            64,
            OPERATION_TIMEOUT);
    private final AtomicLong operationSequence = new AtomicLong();
    private final Map<ModelKey, SManga> mangaByUrl = retainedModels();
    private final Map<ModelKey, SAnime> animeByUrl = retainedModels();
    private final Map<ModelKey, SChapter> chapterByUrl = retainedModels();
    private final Map<ModelKey, SEpisode> episodeByUrl = retainedModels();
    private final Map<PageKey, Page> pageByUrl = retainedModels();

    public ExtensionSourceOperations(ExtensionRuntimeCatalog catalog) {
        this.catalog = Objects.requireNonNull(catalog, "catalog");
    }

    public MangasPage mangaCatalogue(
            long sourceId,
            String operation,
            int page,
            String query,
            Map<String, String> filterValues) {
        return execute(sourceId, ExtensionKind.MANGA, "manga." + operation, "", source -> {
            Object[] arguments;
            if (operation.equals("search")) {
                ExtensionSourceFilterCodec.FilterSet filters = filters(source);
                filters.apply(filterValues);
                arguments = new Object[]{page, Objects.requireNonNullElse(query, ""), filters.abiValue()};
            } else {
                arguments = new Object[]{page};
            }
            String modernName = switch (operation) {
                case "popular" -> "getPopularManga";
                case "latest" -> "getLatestUpdates";
                case "search" -> "getSearchManga";
                default -> throw new IllegalArgumentException("Unknown manga catalogue operation");
            };
            String reactiveName = switch (operation) {
                case "popular" -> "fetchPopularManga";
                case "latest" -> "fetchLatestUpdates";
                case "search" -> "fetchSearchManga";
                default -> throw new IllegalArgumentException("Unknown manga catalogue operation");
            };
            var modern = ExtensionOperationDispatcher.modernOrRx(
                    source, modernName, reactiveName, arguments);
            MangasPage result = modern.available()
                    ? ExtensionOperationDispatcher.result(modern.value(), MangasPage.class)
                    : classicMangaCatalogue(source, operation, arguments);
            result.getMangas().forEach(manga -> mangaByUrl.put(new ModelKey(sourceId, manga.getUrl()), manga));
            return result;
        });
    }

    public MangasPage mangaCatalogue(long sourceId, String operation, int page, String query) {
        return mangaCatalogue(sourceId, operation, page, query, Map.of());
    }

    public AnimesPage animeCatalogue(
            long sourceId,
            String operation,
            int page,
            String query,
            Map<String, String> filterValues) {
        return execute(sourceId, ExtensionKind.ANIME, "anime." + operation, "", source -> {
            Object[] arguments;
            if (operation.equals("search")) {
                ExtensionSourceFilterCodec.FilterSet filters = filters(source);
                filters.apply(filterValues);
                arguments = new Object[]{page, Objects.requireNonNullElse(query, ""), filters.abiValue()};
            } else {
                arguments = new Object[]{page};
            }
            String modernName = switch (operation) {
                case "popular" -> "getPopularAnime";
                case "latest" -> "getLatestUpdates";
                case "search" -> "getSearchAnime";
                default -> throw new IllegalArgumentException("Unknown anime catalogue operation");
            };
            String reactiveName = switch (operation) {
                case "popular" -> "fetchPopularAnime";
                case "latest" -> "fetchLatestUpdates";
                case "search" -> "fetchSearchAnime";
                default -> throw new IllegalArgumentException("Unknown anime catalogue operation");
            };
            var modern = ExtensionOperationDispatcher.modernOrRx(
                    source, modernName, reactiveName, arguments);
            AnimesPage result = modern.available()
                    ? ExtensionOperationDispatcher.result(modern.value(), AnimesPage.class)
                    : classicAnimeCatalogue(source, operation, arguments);
            result.getAnimes().forEach(anime -> animeByUrl.put(new ModelKey(sourceId, anime.getUrl()), anime));
            return result;
        });
    }

    public AnimesPage animeCatalogue(long sourceId, String operation, int page, String query) {
        return animeCatalogue(sourceId, operation, page, query, Map.of());
    }

    public List<FilterDefinition> mangaFilters(long sourceId) {
        return execute(sourceId, ExtensionKind.MANGA, "manga.filters", "", source ->
                definitions(filters(source)));
    }

    public List<FilterDefinition> animeFilters(long sourceId) {
        return execute(sourceId, ExtensionKind.ANIME, "anime.filters", "", source ->
                definitions(filters(source)));
    }

    private static ExtensionSourceFilterCodec.FilterSet filters(Object source) {
        return ExtensionSourceFilterCodec.from(ExtensionOperationDispatcher.invokeAny(source, "getFilterList"));
    }

    private static List<FilterDefinition> definitions(ExtensionSourceFilterCodec.FilterSet filters) {
        return filters.definitions().stream().map(definition -> new FilterDefinition(
                definition.id(),
                definition.label(),
                definition.type().name(),
                definition.options(),
                definition.defaultValue(),
                definition.groupId())).toList();
    }

    public SManga mangaDetails(long sourceId, SourceModel model) {
        return execute(sourceId, ExtensionKind.MANGA, "manga.details", model.url(), source -> {
            SManga manga = manga(sourceId, model);
            SManga result;
            var update = ExtensionOperationDispatcher.suspend(
                    source, "getMangaUpdate", manga, List.of(), true, false);
            if (update.available()) {
                result = ExtensionOperationDispatcher.result(
                        ExtensionOperationDispatcher.invokeAny(update.value(), "getManga"), SManga.class);
            } else {
                var modern = ExtensionOperationDispatcher.modernOrRx(
                        source, "getMangaDetails", "fetchMangaDetails", manga);
                result = modern.available()
                        ? ExtensionOperationDispatcher.result(modern.value(), SManga.class)
                        : requestAndParse(source, "mangaDetailsRequest", new Object[]{manga},
                                "mangaDetailsParse", SManga.class);
            }
            result.setInitialized(true);
            mangaByUrl.put(new ModelKey(sourceId, result.getUrl()), result);
            return result;
        });
    }

    public List<SChapter> chapters(long sourceId, SourceModel model) {
        return execute(sourceId, ExtensionKind.MANGA, "manga.chapters", model.url(), source -> {
            SManga manga = manga(sourceId, model);
            List<SChapter> result;
            var update = ExtensionOperationDispatcher.suspend(
                    source, "getMangaUpdate", manga, List.of(), false, true);
            if (update.available()) {
                result = ExtensionOperationDispatcher.listResult(
                        ExtensionOperationDispatcher.invokeAny(update.value(), "getChapters"), SChapter.class);
                SManga value = ExtensionOperationDispatcher.result(
                        ExtensionOperationDispatcher.invokeAny(update.value(), "getManga"), SManga.class);
                mangaByUrl.put(new ModelKey(sourceId, value.getUrl()), value);
            } else {
                var modern = ExtensionOperationDispatcher.modernOrRx(
                        source, "getChapterList", "fetchChapterList", manga);
                result = modern.available()
                        ? ExtensionOperationDispatcher.listResult(modern.value(), SChapter.class)
                        : ExtensionOperationDispatcher.listResult(requestAndParse(
                                source, "chapterListRequest", new Object[]{manga},
                                "chapterListParse", List.class), SChapter.class);
            }
            result.forEach(chapter -> chapterByUrl.put(new ModelKey(sourceId, chapter.getUrl()), chapter));
            return result;
        });
    }

    public List<Page> pages(long sourceId, String url) {
        return execute(sourceId, ExtensionKind.MANGA, "manga.pages", url, source -> {
            SChapter chapter = chapterByUrl.get(new ModelKey(sourceId, url));
            if (chapter == null) {
                chapter = SChapter.Companion.create();
                chapter.setUrl(url);
            }
            var modern = ExtensionOperationDispatcher.modernOrRx(
                    source, "getPageList", "fetchPageList", chapter);
            List<Page> result = modern.available()
                    ? ExtensionOperationDispatcher.listResult(modern.value(), Page.class)
                    : ExtensionOperationDispatcher.listResult(requestAndParse(
                            source, "pageListRequest", new Object[]{chapter},
                            "pageListParse", List.class), Page.class);
            result.forEach(page -> {
                pageByUrl.put(new PageKey(sourceId, page.getUrl(), page.getIndex()), page);
                if (page.getImageUrl() != null && !page.getImageUrl().isBlank()) {
                    pageByUrl.put(new PageKey(sourceId, page.getImageUrl(), page.getIndex()), page);
                }
            });
            return result;
        });
    }

    public SAnime animeDetails(long sourceId, SourceModel model) {
        return execute(sourceId, ExtensionKind.ANIME, "anime.details", model.url(), source -> {
            SAnime anime = anime(sourceId, model);
            var modern = ExtensionOperationDispatcher.modernOrRx(
                    source, "getAnimeDetails", "fetchAnimeDetails", anime);
            SAnime result = modern.available()
                    ? ExtensionOperationDispatcher.result(modern.value(), SAnime.class)
                    : requestAndParse(source, "animeDetailsRequest", new Object[]{anime},
                            "animeDetailsParse", SAnime.class);
            result.setInitialized(true);
            animeByUrl.put(new ModelKey(sourceId, result.getUrl()), result);
            return result;
        });
    }

    public List<SEpisode> episodes(long sourceId, SourceModel model) {
        return execute(sourceId, ExtensionKind.ANIME, "anime.episodes", model.url(), source -> {
            SAnime anime = anime(sourceId, model);
            List<SEpisode> result;
            var update = ExtensionOperationDispatcher.suspend(
                    source, "getAnimeEpisodeUpdate", anime, List.of(), false, true);
            if (update.available()) {
                result = ExtensionOperationDispatcher.listResult(
                        ExtensionOperationDispatcher.invokeAny(update.value(), "getEpisodes"), SEpisode.class);
            } else {
                var modern = ExtensionOperationDispatcher.modernOrRx(
                        source, "getEpisodeList", "fetchEpisodeList", anime);
                result = modern.available()
                        ? ExtensionOperationDispatcher.listResult(modern.value(), SEpisode.class)
                        : ExtensionOperationDispatcher.listResult(requestAndParse(
                                source, "episodeListRequest", new Object[]{anime},
                                "episodeListParse", List.class), SEpisode.class);
            }
            result.forEach(episode -> episodeByUrl.put(new ModelKey(sourceId, episode.getUrl()), episode));
            return result;
        });
    }

    public List<Video> videos(long sourceId, String url) {
        return execute(sourceId, ExtensionKind.ANIME, "anime.videos", url, source -> {
            SEpisode episode = episodeByUrl.get(new ModelKey(sourceId, url));
            if (episode == null) {
                episode = SEpisode.Companion.create();
                episode.setUrl(url);
            }
            if (ExtensionOperationDispatcher.supportsHosters(source)) {
                var hosters = ExtensionOperationDispatcher.suspend(source, "getHosterList", episode);
                if (hosters.available()) {
                    return hosterVideos(source, hosters.value());
                }
            }
            var modern = ExtensionOperationDispatcher.modernOrRx(
                    source, "getVideoList", "fetchVideoList", episode);
            List<Video> result = modern.available()
                    ? ExtensionOperationDispatcher.listResult(modern.value(), Video.class)
                    : ExtensionOperationDispatcher.listResult(requestAndParse(
                            source, "videoListRequest", new Object[]{episode},
                            "videoListParse", List.class), Video.class);
            if (result.isEmpty()) {
                OkHttpClient client = ExtensionOperationDispatcher.result(
                        ExtensionOperationDispatcher.invokeAny(source, "getClient"), OkHttpClient.class);
                okhttp3.Headers headers = ExtensionOperationDispatcher.result(
                        ExtensionOperationDispatcher.invokeAny(source, "getHeaders"), okhttp3.Headers.class);
                result = EmbeddedVideoFallback.resolve(client, headers, url);
                if (result.isEmpty()) {
                    LOGGER.log(System.Logger.Level.WARNING,
                            "Extension and bounded embed fallback returned no videos; episode host candidates: "
                                    + episodeHosts(url));
                } else {
                    LOGGER.log(System.Logger.Level.INFO,
                            "Bounded embed fallback recovered " + result.size() + " video(s) from: "
                                    + episodeHosts(url));
                }
            }
            return result;
        });
    }

    private static String episodeHosts(String value) {
        var hosts = new TreeSet<String>(String.CASE_INSENSITIVE_ORDER);
        var matches = WEB_HOST.matcher(value);
        while (matches.find()) {
            hosts.add(matches.group(1));
        }
        return hosts.isEmpty() ? "none" : String.join(", ", hosts);
    }

    public ProxiedResource proxy(long sourceId, String url, int pageIndex) {
        return executeAny(sourceId, "source.proxy", url, source -> {
            OkHttpClient client = ExtensionOperationDispatcher.result(
                    ExtensionOperationDispatcher.invokeAny(source, "getClient"), OkHttpClient.class);
            Page page = pageIndex < 0 ? null : pageByUrl.get(new PageKey(sourceId, url, pageIndex));
            Request request = page == null ? directResourceRequest(source, url) : imageRequest(source, page);
            URI location = request.url().uri();
            requireWebLocation(location);
            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    throw new RemoteRequestException("Source resource failed with HTTP " + response.code());
                }
                okhttp3.ResponseBody body = Objects.requireNonNull(response.body(), "Source resource has no body");
                long length = body.contentLength();
                if (length > MAX_PROXY_BYTES) {
                    throw new IllegalStateException("Source resource exceeds the proxy size limit");
                }
                try (InputStream input = body.byteStream()) {
                    byte[] bytes = input.readNBytes(MAX_PROXY_BYTES + 1);
                    if (bytes.length > MAX_PROXY_BYTES) {
                        throw new IllegalStateException("Source resource exceeds the proxy size limit");
                    }
                    String contentType = body.contentType() == null
                            ? "application/octet-stream" : body.contentType().toString();
                    return new ProxiedResource(bytes, contentType);
                }
            } catch (IOException exception) {
                throw new UncheckedIOException("Source resource request failed", exception);
            }
        });
    }

    static Request imageRequest(Object source, Page page) {
        if (page.getImageUrl() == null || page.getImageUrl().isBlank()) {
            var modern = ExtensionOperationDispatcher.modernOrRx(
                    source, "getImageUrl", "fetchImageUrl", page);
            String imageUrl = modern.available()
                    ? ExtensionOperationDispatcher.result(modern.value(), String.class)
                    : requestAndParse(source, "imageUrlRequest", new Object[]{page},
                            "imageUrlParse", String.class);
            page.setImageUrl(requireWebLocation(URI.create(imageUrl)).toASCIIString());
        }
        Request request = ExtensionOperationDispatcher.result(
                ExtensionOperationDispatcher.invokeAny(source, "imageRequest", page), Request.class);
        requireWebLocation(request.url().uri());
        return request;
    }

    private static Request directResourceRequest(Object source, String url) {
        URI location = requireWebLocation(URI.create(url));
        okhttp3.Headers headers = ExtensionOperationDispatcher.result(
                ExtensionOperationDispatcher.invokeAny(source, "getHeaders"), okhttp3.Headers.class);
        return new Request.Builder().url(location.toString()).headers(headers).build();
    }

    private static URI requireWebLocation(URI location) {
        if (!("http".equalsIgnoreCase(location.getScheme()) || "https".equalsIgnoreCase(location.getScheme()))
                || location.getHost() == null) {
            throw new IllegalArgumentException("Proxy URL must be absolute HTTP(S)");
        }
        return location;
    }

    private MangasPage classicMangaCatalogue(Object source, String operation, Object[] arguments) {
        String request = switch (operation) {
            case "popular" -> "popularMangaRequest";
            case "latest" -> "latestUpdatesRequest";
            case "search" -> "searchMangaRequest";
            default -> throw new IllegalArgumentException("Unknown manga catalogue operation");
        };
        String parse = switch (operation) {
            case "popular" -> "popularMangaParse";
            case "latest" -> "latestUpdatesParse";
            case "search" -> "searchMangaParse";
            default -> throw new IllegalArgumentException("Unknown manga catalogue operation");
        };
        return requestAndParse(source, request, arguments, parse, MangasPage.class);
    }

    private AnimesPage classicAnimeCatalogue(Object source, String operation, Object[] arguments) {
        String request = switch (operation) {
            case "popular" -> "popularAnimeRequest";
            case "latest" -> "latestUpdatesRequest";
            case "search" -> "searchAnimeRequest";
            default -> throw new IllegalArgumentException("Unknown anime catalogue operation");
        };
        String parse = switch (operation) {
            case "popular" -> "popularAnimeParse";
            case "latest" -> "latestUpdatesParse";
            case "search" -> "searchAnimeParse";
            default -> throw new IllegalArgumentException("Unknown anime catalogue operation");
        };
        return requestAndParse(source, request, arguments, parse, AnimesPage.class);
    }

    private static <T> T requestAndParse(
            Object source,
            String requestMethod,
            Object[] requestArguments,
            String parseMethod,
            Class<T> resultType) {
        if (!ExtensionOperationDispatcher.hasClassicImplementation(source, parseMethod, (Object) null)) {
            throw new UnsupportedOperationException("Extension does not implement " + parseMethod);
        }
        Request request = ExtensionOperationDispatcher.result(
                ExtensionOperationDispatcher.invokeAny(source, requestMethod, requestArguments), Request.class);
        OkHttpClient client = ExtensionOperationDispatcher.result(
                ExtensionOperationDispatcher.invokeAny(source, "getClient"), OkHttpClient.class);
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new RemoteRequestException(
                        "Source request failed with HTTP " + response.code() + " at " + request.url());
            }
            try {
                return ExtensionOperationDispatcher.result(
                        ExtensionOperationDispatcher.invokeAny(source, parseMethod, response), resultType);
            } catch (RuntimeException | LinkageError failure) {
                throw new ParseException("Extension response parsing failed", failure);
            }
        } catch (IOException exception) {
            throw new UncheckedIOException("Source request failed", exception);
        }
    }

    private static List<Video> hosterVideos(Object source, Object value) {
        List<Hoster> hosters = ExtensionOperationDispatcher.listResult(value, Hoster.class);
        List<Video> videos = new ArrayList<>();
        for (Hoster hoster : hosters) {
            if (hoster.getVideoList() != null) {
                videos.addAll(hoster.getVideoList());
                continue;
            }
            var lazy = ExtensionOperationDispatcher.suspend(source, "getVideoList", hoster);
            if (!lazy.available()) {
                throw new UnsupportedOperationException("Extension does not implement lazy hoster videos");
            }
            videos.addAll(ExtensionOperationDispatcher.listResult(lazy.value(), Video.class));
        }
        return List.copyOf(videos);
    }

    private SManga manga(long sourceId, SourceModel model) {
        ModelKey key = new ModelKey(sourceId, model.url());
        SManga manga = mangaByUrl.get(key);
        if (manga == null) {
            manga = SManga.Companion.create();
        }
        apply(model, manga);
        mangaByUrl.put(key, manga);
        return manga;
    }

    private SAnime anime(long sourceId, SourceModel model) {
        ModelKey key = new ModelKey(sourceId, model.url());
        SAnime anime = animeByUrl.get(key);
        if (anime == null) {
            anime = SAnime.Companion.create();
        }
        apply(model, anime);
        animeByUrl.put(key, anime);
        return anime;
    }

    private static void apply(SourceModel model, SManga manga) {
        manga.setUrl(model.url());
        if (!model.title().isBlank()) manga.setTitle(model.title());
        if (!model.thumbnailUrl().isBlank()) manga.setThumbnail_url(model.thumbnailUrl());
        if (!model.description().isBlank()) manga.setDescription(model.description());
        if (!model.artist().isBlank()) manga.setArtist(model.artist());
        if (!model.author().isBlank()) manga.setAuthor(model.author());
        if (!model.genre().isBlank()) manga.setGenre(model.genre());
        if (model.status() >= 0) manga.setStatus(model.status());
    }

    private static void apply(SourceModel model, SAnime anime) {
        anime.setUrl(model.url());
        if (!model.title().isBlank()) anime.setTitle(model.title());
        if (!model.thumbnailUrl().isBlank()) anime.setThumbnail_url(model.thumbnailUrl());
        if (!model.description().isBlank()) anime.setDescription(model.description());
        if (!model.artist().isBlank()) anime.setArtist(model.artist());
        if (!model.author().isBlank()) anime.setAuthor(model.author());
        if (!model.genre().isBlank()) anime.setGenre(model.genre());
        if (model.status() >= 0) anime.setStatus(model.status());
    }

    private <T> T execute(
            long sourceId,
            ExtensionKind kind,
            String operation,
            String url,
            SourceOperation<T> action) {
        OperationKey key = operationKey(sourceId, operation, url);
        try {
            return operation.endsWith(".search")
                    ? operationPipeline.execute(key, () -> executeDirect(sourceId, kind, operation, url, action))
                    : operationPipeline.executeIndependent(
                            key,
                            () -> executeDirect(sourceId, kind, operation, url, action));
        } catch (TaskPipelineException failure) {
            throw pipelineFailure(sourceId, kind.name(), operation, url, failure);
        }
    }

    private <T> T executeDirect(
            long sourceId,
            ExtensionKind kind,
            String operation,
            String url,
            SourceOperation<T> action) {
        try (ExtensionRuntimeCatalog.Snapshot snapshot = catalog.discover()) {
            LoadedSource loaded = snapshot.sources().stream()
                    .filter(source -> source.id() == sourceId && source.kind() == kind)
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Source is not installed"));
            return invoke(loaded, operation, url, action);
        }
    }

    private <T> T executeAny(long sourceId, String operation, String url, SourceOperation<T> action) {
        OperationKey key = operationKey(sourceId, operation, url);
        try {
            return operationPipeline.executeIndependent(
                    key,
                    () -> executeAnyDirect(sourceId, operation, url, action));
        } catch (TaskPipelineException failure) {
            throw pipelineFailure(sourceId, "external-source", operation, url, failure);
        }
    }

    private <T> T executeAnyDirect(long sourceId, String operation, String url, SourceOperation<T> action) {
        try (ExtensionRuntimeCatalog.Snapshot snapshot = catalog.discover()) {
            LoadedSource loaded = snapshot.sources().stream()
                    .filter(source -> source.id() == sourceId)
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Source is not installed"));
            return invoke(loaded, operation, url, action);
        }
    }

    private OperationKey operationKey(long sourceId, String operation, String url) {
        long sequence = operation.endsWith(".search") ? 0L : operationSequence.incrementAndGet();
        return new OperationKey(sourceId, operation, Objects.requireNonNullElse(url, ""), sequence);
    }

    private static ExtensionOperationException pipelineFailure(
            long sourceId,
            String packageName,
            String operation,
            String url,
            TaskPipelineException failure) {
        ExtensionOperationException.Code code = switch (failure.reason()) {
            case SUPERSEDED -> ExtensionOperationException.Code.OPERATION_SUPERSEDED;
            case TIMED_OUT -> ExtensionOperationException.Code.OPERATION_TIMEOUT;
            case BUSY, CLOSED, INTERRUPTED -> ExtensionOperationException.Code.HOST_BUSY;
        };
        return new ExtensionOperationException(code, operation, packageName, sourceId, url, failure);
    }

    private static <T> T invoke(
            LoadedSource source,
            String operation,
            String url,
            SourceOperation<T> action) {
        try {
            return action.apply(source.instance());
        } catch (IllegalArgumentException failure) {
            throw failure;
        } catch (RuntimeException | LinkageError failure) {
            throw new ExtensionOperationException(
                    classify(failure), operation, source.packageName(), source.id(), url, failure);
        }
    }

    private static ExtensionOperationException.Code classify(Throwable failure) {
        if (failure instanceof UnsupportedOperationException) {
            return ExtensionOperationException.Code.UNSUPPORTED_CAPABILITY;
        }
        if (failure instanceof RemoteRequestException || failure instanceof UncheckedIOException) {
            return ExtensionOperationException.Code.REMOTE_HTTP_FAILURE;
        }
        if (failure instanceof ParseException) {
            return ExtensionOperationException.Code.PARSE_FAILURE;
        }
        if (failure instanceof ExtensionOperationDispatcher.AbiException
                || failure instanceof ReflectiveOperationException
                || failure instanceof LinkageError
                || failure instanceof ClassCastException) {
            return ExtensionOperationException.Code.ABI_FAILURE;
        }
        return ExtensionOperationException.Code.INTERNAL_HOST_FAILURE;
    }

    private static <K, V> Map<K, V> retainedModels() {
        return Collections.synchronizedMap(new LinkedHashMap<>(64, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
                return size() > MAX_RETAINED_MODELS;
            }
        });
    }

    @FunctionalInterface
    private interface SourceOperation<T> {
        T apply(Object source);
    }

    private record ModelKey(long sourceId, String url) {
        private ModelKey {
            Objects.requireNonNull(url, "url");
        }
    }

    private record PageKey(long sourceId, String url, int index) {
        private PageKey {
            Objects.requireNonNull(url, "url");
            if (index < 0) throw new IllegalArgumentException("page index must not be negative");
        }
    }

    private static final class RemoteRequestException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        private RemoteRequestException(String message) {
            super(message);
        }
    }

    private static final class ParseException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        private ParseException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public record SourceModel(
            String url,
            String title,
            String thumbnailUrl,
            String description,
            String artist,
            String author,
            String genre,
            int status) {
        public SourceModel {
            url = require(url, "url");
            title = Objects.requireNonNullElse(title, "").strip();
            thumbnailUrl = Objects.requireNonNullElse(thumbnailUrl, "").strip();
            description = Objects.requireNonNullElse(description, "").strip();
            artist = Objects.requireNonNullElse(artist, "").strip();
            author = Objects.requireNonNullElse(author, "").strip();
            genre = Objects.requireNonNullElse(genre, "").strip();
        }

        private static String require(String value, String label) {
            String result = Objects.requireNonNull(value, label).strip();
            if (result.isEmpty()) {
                throw new IllegalArgumentException(label + " must not be blank");
            }
            return result;
        }
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

    public record FilterDefinition(
            String id,
            String label,
            String type,
            List<String> options,
        String defaultValue,
        String groupId) {
        public FilterDefinition {
            id = Objects.requireNonNull(id, "filter id").strip();
            label = Objects.requireNonNull(label, "filter label");
            type = Objects.requireNonNull(type, "filter type").strip();
            options = List.copyOf(Objects.requireNonNull(options, "filter options"));
            defaultValue = Objects.requireNonNull(defaultValue, "filter default value");
            groupId = Objects.requireNonNull(groupId, "filter group id");
            if (id.isEmpty() || type.isEmpty()) {
                throw new IllegalArgumentException("filter id and type must not be blank");
            }
        }
    }

    private record OperationKey(long sourceId, String operation, String url, long sequence) {
    }

    @Override
    public void close() {
        operationPipeline.close();
    }
}
