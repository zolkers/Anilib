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
import fr.vriege.anilib.feature.source.SourceExtensionManifest;
import fr.vriege.anilib.feature.source.SourceExtensionPlugin;
import fr.vriege.anilib.feature.source.SourceFilterDefinition;
import fr.vriege.anilib.feature.source.SourceId;
import fr.vriege.anilib.feature.source.SourcePage;
import fr.vriege.anilib.feature.source.SourcePageResource;
import fr.vriege.anilib.feature.source.SourcePreferenceDefinition;
import fr.vriege.anilib.feature.source.SourceSearchRequest;
import fr.vriege.anilib.feature.source.WebSource;
import fr.vriege.anilib.foundation.component.ComponentDescriptor;
import fr.vriege.anilib.foundation.validation.Preconditions;
import fr.vriege.anilib.kernel.AnilibPlugin;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;

public final class AniyomiMangaSourceAdapter {
    private static final SourceApiVersion REQUIRED_API = new SourceApiVersion(1, 4);
    private static final int MAX_PAGE_BYTES = 32 * 1024 * 1024;

    private AniyomiMangaSourceAdapter() {
    }

    public static AdaptedSource adapt(
            String packageName,
            String extensionVersion,
            Object delegate) {
        return adapt(
                packageName,
                extensionVersion,
                delegate,
                () -> true,
                AniyomiSourcePreferences.empty());
    }

    public static AdaptedSource adapt(
            String packageName,
            String extensionVersion,
            Object delegate,
            BooleanSupplier authorized,
            AniyomiSourcePreferences preferences) {
        Preconditions.requireNonBlank(packageName, "packageName");
        Preconditions.requireNonBlank(extensionVersion, "extensionVersion");
        ReflectedMangaSource bridge = new ReflectedMangaSource(
                extensionVersion,
                Preconditions.requireNonNull(delegate, "delegate"),
                Preconditions.requireNonNull(authorized, "authorized"),
                Preconditions.requireNonNull(preferences, "preferences"));
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

    private static final class ReflectedMangaSource implements CatalogueSource, PagedSource, WebSource {
        private final Object delegate;
        private final BooleanSupplier authorized;
        private final AniyomiSourcePreferences preferences;
        private final SourceDescriptor descriptor;
        private final Map<SourceCatalogueItemId, Object> mangaById = new ConcurrentHashMap<>();
        private final Map<SourceContentUnitId, Object> chapterById = new ConcurrentHashMap<>();
        private final Map<SourcePageResource, Object> pageByResource = new ConcurrentHashMap<>();

        private ReflectedMangaSource(
                String extensionVersion,
                Object delegate,
                BooleanSupplier authorized,
                AniyomiSourcePreferences preferences) {
            this.delegate = delegate;
            this.authorized = authorized;
            this.preferences = preferences;
            long numericId = AniyomiAnimeSourceAdapter.number(
                    AniyomiAnimeSourceAdapter.invoke(delegate, "getId")).longValue();
            SourceId sourceId = SourceId.of(AniyomiAnimeSourceAdapter.sourceId(numericId));
            descriptor = new SourceDescriptor(
                    sourceId,
                    AniyomiAnimeSourceAdapter.text(
                            AniyomiAnimeSourceAdapter.invoke(delegate, "getName"),
                            "source name"),
                    extensionVersion,
                    AniyomiAnimeSourceAdapter.language(
                            AniyomiAnimeSourceAdapter.invokeOptional(delegate, "getLang").orElse("und")),
                    Set.of(SourceContentKind.MANGA),
                    REQUIRED_API);
            requireEitherMethod("getPopularManga", 2, "fetchPopularManga", 1);
            requireEitherMethod("getSearchManga", 4, "fetchSearchManga", 3);
            requireChapterApi();
            requireEitherMethod("getPageList", 2, "fetchPageList", 1);
        }

        @Override
        public SourceDescriptor descriptor() {
            return descriptor;
        }

        @Override
        public URI homePage() {
            requireAuthorized();
            return AniyomiAnimeSourceAdapter.webHomePage(delegate);
        }

        @Override
        public Optional<URI> titlePage(SourceCatalogueItemId itemId) {
            requireAuthorized();
            requireOwned(itemId);
            return AniyomiAnimeSourceAdapter.resolveWebPage(delegate, itemId.value());
        }

        @Override
        public Map<String, String> browserHeaders(URI location) {
            requireAuthorized();
            return AniyomiAnimeSourceAdapter.safeBrowserHeaders(delegate);
        }

        @Override
        public Optional<String> browserUserAgent(URI location) {
            requireAuthorized();
            return AniyomiAnimeSourceAdapter.sourceUserAgent(delegate);
        }

        @Override
        public SourcePage popular(SourceBrowseRequest request) {
            requireAuthorized();
            SourceBrowseRequest value = Preconditions.requireNonNull(request, "request");
            preferences.apply(value.preferences());
            return page(AniyomiAnimeSourceAdapter.invokeModernOrRx(
                    delegate,
                    "getPopularManga",
                    "fetchPopularManga",
                    value.page()));
        }

        @Override
        public boolean supportsLatest() {
            return AniyomiAnimeSourceAdapter.invokeOptional(delegate, "getSupportsLatest")
                    .map(AniyomiAnimeSourceAdapter::bool)
                    .orElse(false);
        }

        @Override
        public SourcePage latest(SourceBrowseRequest request) {
            requireAuthorized();
            SourceBrowseRequest value = Preconditions.requireNonNull(request, "request");
            if (!supportsLatest()) {
                return CatalogueSource.super.latest(value);
            }
            preferences.apply(value.preferences());
            return page(AniyomiAnimeSourceAdapter.invokeModernOrRx(
                    delegate,
                    "getLatestUpdates",
                    "fetchLatestUpdates",
                    value.page()));
        }

        @Override
        public SourcePage search(SourceSearchRequest request) {
            requireAuthorized();
            SourceSearchRequest value = Preconditions.requireNonNull(request, "request");
            preferences.apply(value.browseRequest().preferences());
            AniyomiAnimeFilterAdapter.ReflectedFilters filters = reflectedFilters();
            filters.apply(value.browseRequest().filters());
            return page(AniyomiAnimeSourceAdapter.invokeModernOrRx(
                    delegate,
                    "getSearchManga",
                    "fetchSearchManga",
                    value.browseRequest().page(),
                    value.query(),
                    filters.abiValue()));
        }

        @Override
        public List<SourceFilterDefinition> filters() {
            return reflectedFilters().definitions();
        }

        @Override
        public List<SourcePreferenceDefinition> preferences() {
            return preferences.definitions();
        }

        @Override
        public List<SourceContentUnit> contentUnits(SourceCatalogueItemId itemId) {
            requireAuthorized();
            requireOwned(itemId);
            Object manga = Optional.ofNullable(mangaById.get(itemId))
                    .orElseThrow(() -> new IllegalArgumentException("Manga is not loaded in this APK source"));
            List<?> chapters = chapterList(manga);
            List<SourceContentUnit> units = new ArrayList<>(chapters.size());
            for (Object chapter : chapters) {
                String url = AniyomiAnimeSourceAdapter.firstText(chapter, "getUrl");
                SourceContentUnitId id = new SourceContentUnitId(itemId, url);
                SourceContentUnit unit = new SourceContentUnit(
                        id,
                        AniyomiAnimeSourceAdapter.firstText(chapter, "getName"),
                        AniyomiAnimeSourceAdapter.uploadedAt(
                                AniyomiAnimeSourceAdapter.invokeOptional(chapter, "getDate_upload").orElse(null)));
                chapterById.put(id, chapter);
                units.add(unit);
            }
            return List.copyOf(units);
        }

        @Override
        public List<SourcePageResource> pages(SourceContentUnitId contentUnitId) {
            requireAuthorized();
            SourceContentUnitId id = Preconditions.requireNonNull(contentUnitId, "contentUnitId");
            requireOwned(id.itemId());
            Object chapter = Optional.ofNullable(chapterById.get(id))
                    .orElseThrow(() -> new IllegalArgumentException("Chapter is not loaded in this APK source"));
            List<?> pages = AniyomiAnimeSourceAdapter.list(
                    AniyomiAnimeSourceAdapter.invokeModernOrRx(
                            delegate,
                            "getPageList",
                            "fetchPageList",
                            chapter),
                    "manga page list");
            List<SourcePageResource> resources = new ArrayList<>(pages.size());
            for (int index = 0; index < pages.size(); index++) {
                Object page = pages.get(index);
                String location = initialPageLocation(page);
                String identity = (location.isBlank() ? "page" : location) + "#anilib-index=" + index;
                SourcePageResource resource = new SourcePageResource(
                        id,
                        identity,
                        index,
                        SourcePageResource.UNKNOWN_SIZE);
                pageByResource.put(resource, page);
                resources.add(resource);
            }
            return List.copyOf(resources);
        }

        @Override
        public byte[] readPage(SourcePageResource page) {
            requireAuthorized();
            SourcePageResource resource = Preconditions.requireNonNull(page, "page");
            requireOwned(resource.contentUnitId().itemId());
            Object abiPage = Optional.ofNullable(pageByResource.get(resource))
                    .orElseThrow(() -> new IllegalArgumentException("Page is not loaded in this APK source"));
            return download(abiPage);
        }

        private SourcePage page(Object abiPage) {
            List<?> mangas = AniyomiAnimeSourceAdapter.list(
                    AniyomiAnimeSourceAdapter.invoke(abiPage, "getMangas"),
                    "manga catalogue page");
            List<SourceCatalogueItem> items = new ArrayList<>(mangas.size());
            for (Object manga : mangas) {
                String url = AniyomiAnimeSourceAdapter.firstText(manga, "getUrl");
                SourceCatalogueItemId itemId = new SourceCatalogueItemId(descriptor.id(), url);
                SourceCatalogueItem item = new SourceCatalogueItem(
                        itemId,
                        AniyomiAnimeSourceAdapter.firstText(manga, "getTitle"),
                        AniyomiAnimeSourceAdapter.nullableText(
                                AniyomiAnimeSourceAdapter.invokeOptional(manga, "getDescription").orElse(null)),
                        AniyomiAnimeSourceAdapter.absoluteUri(
                                AniyomiAnimeSourceAdapter.invokeOptional(manga, "getThumbnail_url").orElse(null)),
                        SourceContentKind.MANGA);
                mangaById.put(itemId, manga);
                items.add(item);
            }
            boolean hasNextPage = AniyomiAnimeSourceAdapter.bool(
                    AniyomiAnimeSourceAdapter.invoke(abiPage, "getHasNextPage"));
            return new SourcePage(items, hasNextPage);
        }

        private List<?> chapterList(Object manga) {
            if (AniyomiAnimeSourceAdapter.hasCompatibleSuspendMethod(
                    delegate,
                    "getMangaUpdate",
                    manga,
                    List.of(),
                    false,
                    true)) {
                Object update = AniyomiAnimeSourceAdapter.invokeSuspend(
                        delegate,
                        "getMangaUpdate",
                        manga,
                        List.of(),
                        false,
                        true);
                return AniyomiAnimeSourceAdapter.list(
                        AniyomiAnimeSourceAdapter.invoke(update, "getChapters"),
                        "manga chapter update");
            }
            return AniyomiAnimeSourceAdapter.list(
                    AniyomiAnimeSourceAdapter.invokeModernOrRx(
                            delegate,
                            "getChapterList",
                            "fetchChapterList",
                            manga),
                    "manga chapter list");
        }

        private String initialPageLocation(Object page) {
            String image = AniyomiAnimeSourceAdapter.nullableText(
                    AniyomiAnimeSourceAdapter.invokeOptional(page, "getImageUrl").orElse(null));
            return image.isBlank()
                    ? AniyomiAnimeSourceAdapter.nullableText(
                            AniyomiAnimeSourceAdapter.invokeOptional(page, "getUrl").orElse(null))
                    : image;
        }

        private URI pageLocation(Object page) {
            String location = AniyomiAnimeSourceAdapter.nullableText(
                    AniyomiAnimeSourceAdapter.invokeOptional(page, "getImageUrl").orElse(null));
            if (location.isBlank()) {
                Object resolved;
                if (AniyomiAnimeSourceAdapter.hasCompatibleSuspendMethod(delegate, "getImageUrl", page)) {
                    resolved = AniyomiAnimeSourceAdapter.invokeSuspend(delegate, "getImageUrl", page);
                } else {
                    resolved = AniyomiAnimeSourceAdapter.await(
                            AniyomiAnimeSourceAdapter.invoke(delegate, "fetchImageUrl", page));
                }
                location = AniyomiAnimeSourceAdapter.nullableText(resolved);
                if (!location.isBlank() && AniyomiAnimeSourceAdapter.hasMethod(page, "setImageUrl", 1)) {
                    AniyomiAnimeSourceAdapter.invoke(page, "setImageUrl", location);
                }
            }
            return AniyomiAnimeSourceAdapter.absoluteUri(location)
                    .orElseThrow(() -> new IllegalStateException("APK manga page location must be absolute"));
        }

        private byte[] download(Object page) {
            Object response = null;
            try {
                Object client = AniyomiAnimeSourceAdapter.invoke(delegate, "getClient");
                URI location = pageLocation(page);
                Object request;
                if (AniyomiAnimeSourceAdapter.hasMethod(delegate, "imageRequest", 1)) {
                    request = AniyomiAnimeSourceAdapter.invoke(delegate, "imageRequest", page);
                } else {
                    ClassLoader classLoader = delegate.getClass().getClassLoader();
                    Class<?> builderType = Class.forName("okhttp3.Request$Builder", true, classLoader);
                    Constructor<?> constructor = builderType.getConstructor();
                    Object builder = constructor.newInstance();
                    AniyomiAnimeSourceAdapter.invoke(builder, "url", location.toString());
                    AniyomiAnimeSourceAdapter.invokeOptional(delegate, "getHeaders")
                            .ifPresent(headers -> AniyomiAnimeSourceAdapter.invoke(builder, "headers", headers));
                    request = AniyomiAnimeSourceAdapter.invoke(builder, "build");
                }
                Object call = AniyomiAnimeSourceAdapter.invoke(client, "newCall", request);
                response = AniyomiAnimeSourceAdapter.invoke(call, "execute");
                Object currentResponse = response;
                boolean successful = AniyomiAnimeSourceAdapter.invokeOptional(currentResponse, "isSuccessful")
                        .map(AniyomiAnimeSourceAdapter::bool)
                        .orElse(true);
                if (!successful) {
                    throw new IllegalStateException("APK manga page request failed");
                }
                Object body = AniyomiAnimeSourceAdapter.invokeOptional(currentResponse, "body")
                        .or(() -> AniyomiAnimeSourceAdapter.invokeOptional(currentResponse, "getBody"))
                        .orElseThrow(() -> new IllegalStateException("APK manga page response has no body"));
                Object stream = AniyomiAnimeSourceAdapter.invoke(body, "byteStream");
                if (!(stream instanceof InputStream input)) {
                    throw new IllegalStateException("APK manga page body has no input stream");
                }
                try (input) {
                    byte[] bytes = input.readNBytes(MAX_PAGE_BYTES + 1);
                    if (bytes.length > MAX_PAGE_BYTES) {
                        throw new IllegalStateException("APK manga page exceeds 32 MiB");
                    }
                    return bytes;
                }
            } catch (ClassNotFoundException | NoSuchMethodException | InstantiationException
                     | IllegalAccessException exception) {
                throw new IllegalStateException("APK manga HTTP ABI is unavailable", exception);
            } catch (InvocationTargetException exception) {
                throw new IllegalStateException("Unable to construct APK manga page request", exception.getCause());
            } catch (IOException exception) {
                throw new IllegalStateException("Unable to read APK manga page", exception);
            } finally {
                if (response != null) {
                    AniyomiAnimeSourceAdapter.invokeOptional(response, "close");
                }
            }
        }

        private AniyomiAnimeFilterAdapter.ReflectedFilters reflectedFilters() {
            Object filters = AniyomiAnimeSourceAdapter.invokeOptional(delegate, "getFilterList")
                    .orElse(List.of());
            return AniyomiAnimeFilterAdapter.from(filters);
        }

        private void requireEitherMethod(
                String suspendMethod,
                int suspendParameterCount,
                String rxMethod,
                int rxParameterCount) {
            if (!AniyomiAnimeSourceAdapter.hasMethod(delegate, suspendMethod, suspendParameterCount)
                    && !AniyomiAnimeSourceAdapter.hasMethod(delegate, rxMethod, rxParameterCount)) {
                throw new IllegalArgumentException(
                        "Object is not a supported manga APK source; missing "
                                + suspendMethod + " or " + rxMethod);
            }
        }

        private void requireChapterApi() {
            if (!AniyomiAnimeSourceAdapter.hasMethod(delegate, "getMangaUpdate", 5)
                    && !AniyomiAnimeSourceAdapter.hasMethod(delegate, "getChapterList", 2)
                    && !AniyomiAnimeSourceAdapter.hasMethod(delegate, "fetchChapterList", 1)) {
                throw new IllegalArgumentException("Object is not a supported manga APK source; missing chapter API");
            }
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
}
