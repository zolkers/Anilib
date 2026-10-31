package fr.vriege.anilib.platform.desktopextensionhost.extension;

import fr.vriege.anilib.platform.desktopextensionhost.compat.aniyomi.animesource.online.ParsedAnimeHttpSource;

import java.util.ArrayList;
import java.util.List;

final class ExtensionCompatibility {
    private ExtensionCompatibility() {}

    static void requireSupported(ExtensionKind kind, Object source) {
        List<String> missing = kind == ExtensionKind.MANGA ? mangaGaps(source) : animeGaps(source);
        if (!missing.isEmpty()) {
            throw new IllegalStateException(
                    "Unsupported desktop capabilities: " + String.join(", ", missing));
        }
    }

    private static List<String> mangaGaps(Object source) {
        List<String> missing = new ArrayList<>();
        require(
                missing,
                source,
                "popular catalogue",
                method("getPopularManga", 2),
                method("fetchPopularManga", 1),
                method("popularMangaParse", 1));
        require(
                missing,
                source,
                "search catalogue",
                method("getSearchManga", 4),
                method("fetchSearchManga", 3),
                method("searchMangaParse", 1));
        require(
                missing,
                source,
                "title details",
                method("getMangaDetails", 2),
                method("fetchMangaDetails", 1),
                method("mangaDetailsParse", 1));
        require(
                missing,
                source,
                "chapter list",
                method("getMangaUpdate", 5),
                method("getChapterList", 2),
                method("fetchChapterList", 1),
                method("chapterListParse", 1));
        require(
                missing,
                source,
                "page list",
                method("getPageList", 2),
                method("fetchPageList", 1),
                method("pageListParse", 1));
        return List.copyOf(missing);
    }

    private static List<String> animeGaps(Object source) {
        List<String> missing = new ArrayList<>();
        require(
                missing,
                source,
                "popular catalogue",
                method("getPopularAnime", 2),
                method("fetchPopularAnime", 1),
                method("popularAnimeParse", 1));
        require(
                missing,
                source,
                "search catalogue",
                method("getSearchAnime", 4),
                method("fetchSearchAnime", 3),
                method("searchAnimeParse", 1));
        require(
                missing,
                source,
                "title details",
                method("getAnimeDetails", 2),
                method("fetchAnimeDetails", 1),
                method("animeDetailsParse", 1));
        require(
                missing,
                source,
                "episode list",
                method("getAnimeEpisodeUpdate", 5),
                method("getEpisodeList", 2),
                method("fetchEpisodeList", 1),
                method("episodeListParse", 1));
        require(
                missing,
                source,
                "video or hoster list",
                method("hosterListParse", 1),
                method("getHosterList", 2),
                method("getVideoList", 2),
                method("fetchVideoList", 1),
                method("videoListParse", 1));
        return List.copyOf(missing);
    }

    private static void require(
            List<String> missing,
            Object source,
            String capability,
            MethodSignature... alternatives) {
        for (MethodSignature alternative : alternatives) {
            if (ExtensionOperationDispatcher.hasExtensionMethod(
                            source, alternative.name(), alternative.parameterCount())
                    || hasParsedImplementation(source, alternative.name())) {
                return;
            }
        }
        missing.add(capability);
    }

    static boolean hasParsedImplementation(Object source, String method) {
        if (!(source instanceof ParsedAnimeHttpSource)) return false;
        return switch (method) {
            case "popularAnimeParse" ->
                    hooks(source, "popularAnimeSelector", "popularAnimeFromElement");
            case "searchAnimeParse" ->
                    hooks(source, "searchAnimeSelector", "searchAnimeFromElement");
            case "latestUpdatesParse" ->
                    hooks(source, "latestUpdatesSelector", "latestUpdatesFromElement");
            case "episodeListParse" -> hooks(source, "episodeListSelector", "episodeFromElement");
            case "seasonListParse" -> hooks(source, "seasonListSelector", "seasonFromElement");
            case "hosterListParse" -> hooks(source, "hosterListSelector", "hosterFromElement");
            case "videoListParse" -> hooks(source, "videoListSelector", "videoFromElement");
            case "animeDetailsParse", "videoUrlParse" ->
                    ExtensionOperationDispatcher.hasExtensionMethod(source, method, 1);
            default -> false;
        };
    }

    private static boolean hooks(Object source, String selector, String element) {
        return ExtensionOperationDispatcher.hasExtensionMethod(source, selector, 0)
                && ExtensionOperationDispatcher.hasExtensionMethod(source, element, 1);
    }

    private static MethodSignature method(String name, int parameterCount) {
        return new MethodSignature(name, parameterCount);
    }

    private record MethodSignature(String name, int parameterCount) {}
}
