package fr.vriege.anilib.platform.desktopextensionhost;

import fr.vriege.anilib.platform.desktopextensionhost.extension.ExtensionRegistry;
import fr.vriege.anilib.platform.desktopextensionhost.extension.InstalledExtension;
import fr.vriege.anilib.platform.desktopextensionhost.protocol.DesktopExtensionHostProtocol;
import fr.vriege.anilib.platform.desktopextensionhost.server.DesktopExtensionHostServer;

import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ExtensionCompatibilitySmoke {
    private ExtensionCompatibilitySmoke() {
    }

    public static void main(String[] arguments) throws Exception {
        if (arguments.length != 10) {
            throw new IllegalArgumentException("Expected a path, package, version, SHA-256, and repository revision "
                    + "for both manga and anime fixtures");
        }
        FixturePin mangaPin = FixturePin.from(arguments, 0);
        FixturePin animePin = FixturePin.from(arguments, 5);
        Path data = Files.createTempDirectory("anilib-extension-compatibility-");
        try {
            ExtensionRegistry registry = new ExtensionRegistry(data);
            verifyPin(mangaPin, registry.install(mangaPin.path()));
            verifyPin(animePin, registry.install(animePin.path()));
            try (DesktopExtensionHostServer server = DesktopExtensionHostServer.open(
                    InetAddress.getLoopbackAddress(), 0, data)) {
                server.start();
                String sources = get(server, DesktopExtensionHostProtocol.SOURCES_PATH);
                String mangaId = sourceId(sources, "MangaDex", null);
                String animeId = sourceId(sources, "Anime-Sama", "fr");
                verifyMangaWorkflow(server, mangaId);
                verifyAnimeWorkflow(server, animeId);
                verifyUninstall(server, mangaPin.packageName());
                verifyUninstall(server, animePin.packageName());
                String installed = get(server, DesktopExtensionHostProtocol.INSTALLED_EXTENSIONS_PATH);
                if (!installed.contains("\"extensions\":[]")) {
                    throw new IllegalStateException("Uninstalled extensions remain in the inventory: " + installed);
                }
            }
        } finally {
            try (var paths = Files.walk(data)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(path);
                }
            }
        }
    }

    private static void verifyPin(FixturePin pin, InstalledExtension installed) throws Exception {
        String actualSha256 = HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(pin.path())));
        if (!actualSha256.equals(pin.sha256())) {
            throw new IllegalStateException("Fixture SHA-256 differs from pin at " + pin.repositoryRevision());
        }
        if (!installed.metadata().packageName().equals(pin.packageName())
                || !installed.metadata().versionName().equals(pin.versionName())) {
            throw new IllegalStateException("Fixture metadata differs from pin at " + pin.repositoryRevision());
        }
    }

    private static String get(DesktopExtensionHostServer server, String path) throws Exception {
        HttpResponse<String> response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + server.port() + path)).build(),
                HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IllegalStateException("Extension endpoint failed: " + path + ' ' + response.body());
        }
        return response.body();
    }

    private static void verifyUninstall(DesktopExtensionHostServer server, String packageName) throws Exception {
        HttpResponse<String> response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + server.port()
                                + DesktopExtensionHostProtocol.UNINSTALL_EXTENSION_PATH))
                        .header("content-type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString("{\"pkg\":\"" + packageName + "\"}"))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200 || !response.body().contains("\"ok\":true")) {
            throw new IllegalStateException("Extension uninstall failed for " + packageName + ": " + response.body());
        }
        String sources = get(server, DesktopExtensionHostProtocol.SOURCES_PATH);
        if (sources.contains("\"pkg\":\"" + packageName + "\"")) {
            throw new IllegalStateException("Uninstalled extension still exposes sources: " + packageName);
        }
    }

    private static String sourceId(String document, String name, String language) {
        Pattern pattern = Pattern.compile("\\{\\\"id\\\":\\\"([0-9]+)\\\",\\\"name\\\":\\\""
                + Pattern.quote(name) + "\\\",\\\"lang\\\":\\\""
                + (language == null ? "[^\\\"]+" : Pattern.quote(language)) + "\\\"");
        Matcher matcher = pattern.matcher(document);
        if (!matcher.find()) {
            throw new IllegalStateException("Expected source is unavailable: " + name
                    + (language == null ? "" : " (" + language + ')') + ' ' + document);
        }
        return matcher.group(1);
    }

    private static void verifyCatalogue(String document, String field) {
        if (!document.contains("\"" + field + "\":[{") || !document.contains("\"title\":")) {
            throw new IllegalStateException("Extension catalogue is empty or malformed: " + document);
        }
    }

    private static void verifyMangaWorkflow(DesktopExtensionHostServer server, String sourceId) throws Exception {
        String catalogue = get(server, DesktopExtensionHostProtocol.MANGA_PATH + sourceId + "/popular?page=1");
        verifyCatalogue(catalogue, "mangas");
        String titleUrl = first(catalogue, "mangas", "url");
        String title = first(catalogue, "mangas", "title");
        String model = "?url=" + encode(titleUrl) + "&title=" + encode(title);
        String detailsPath = DesktopExtensionHostProtocol.MANGA_PATH + sourceId + "/details" + model;
        requireObject(get(server, detailsPath), "manga");
        String chaptersPath = DesktopExtensionHostProtocol.MANGA_PATH + sourceId + "/chapters" + model;
        String chapters = requireArray(get(server, chaptersPath), "chapters");
        String chapterUrl = first(chapters, "chapters", "url");
        String pagesPath = DesktopExtensionHostProtocol.MANGA_PATH + sourceId + "/pages?url=" + encode(chapterUrl);
        requireArray(get(server, pagesPath), "pages");
        requireObject(get(server, detailsPath), "manga");
        requireArray(get(server, chaptersPath), "chapters");
        requireArray(get(server, pagesPath), "pages");
    }

    private static void verifyAnimeWorkflow(DesktopExtensionHostServer server, String sourceId) throws Exception {
        String catalogue = get(server, DesktopExtensionHostProtocol.ANIME_PATH
                + sourceId + "/search?page=1&query=one%20piece");
        verifyCatalogue(catalogue, "animes");
        String titleUrl = first(catalogue, "animes", "url");
        String title = first(catalogue, "animes", "title");
        String model = "?url=" + encode(titleUrl) + "&title=" + encode(title);
        String detailsPath = DesktopExtensionHostProtocol.ANIME_PATH + sourceId + "/details" + model;
        requireObject(get(server, detailsPath), "anime");
        String episodesPath = DesktopExtensionHostProtocol.ANIME_PATH + sourceId + "/episodes" + model;
        String episodes = requireArray(get(server, episodesPath), "episodes");
        String episodeUrl = first(episodes, "episodes", "url");
        String videosPath = DesktopExtensionHostProtocol.ANIME_PATH + sourceId + "/videos?url=" + encode(episodeUrl);
        String videos = requireArray(get(server, videosPath), "videos");
        if (!videos.contains("\"headers\":") || !videos.contains("\"subtitleTracks\":")) {
            throw new IllegalStateException("Anime stream projection lost headers or subtitles: " + videos);
        }
        requireObject(get(server, detailsPath), "anime");
        requireArray(get(server, episodesPath), "episodes");
        requireArray(get(server, videosPath), "videos");
    }

    private static String requireArray(String document, String field) {
        if (!document.contains("\"" + field + "\":[{")) {
            throw new IllegalStateException("Extension response has an empty " + field + " array: " + document);
        }
        return document;
    }

    private static String requireObject(String document, String field) {
        if (!document.contains("\"" + field + "\":{")) {
            throw new IllegalStateException("Extension response has no " + field + " object: " + document);
        }
        return document;
    }

    private static String first(String document, String collection, String field) {
        Pattern pattern = Pattern.compile("\\\"" + Pattern.quote(collection)
                + "\\\":\\[\\{.*?\\\"" + Pattern.quote(field) + "\\\":\\\"((?:\\\\.|[^\\\"])*)\\\"",
                Pattern.DOTALL);
        Matcher matcher = pattern.matcher(document);
        if (!matcher.find()) {
            throw new IllegalStateException("Extension response has no " + field + " in " + collection);
        }
        return unescape(matcher.group(1));
    }

    private static String unescape(String value) {
        return value.replace("\\/", "/")
                .replace("\\\"", "\"")
                .replace("\\\\", "\\");
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private record FixturePin(
            Path path,
            String packageName,
            String versionName,
            String sha256,
            String repositoryRevision) {
        private static FixturePin from(String[] arguments, int offset) {
            Path path = Path.of(arguments[offset]).toAbsolutePath().normalize();
            String packageName = required(arguments[offset + 1], "packageName");
            String versionName = required(arguments[offset + 2], "versionName");
            String sha256 = required(arguments[offset + 3], "sha256").toLowerCase(java.util.Locale.ROOT);
            String revision = required(arguments[offset + 4], "repositoryRevision");
            if (!sha256.matches("[0-9a-f]{64}")) {
                throw new IllegalArgumentException("Fixture SHA-256 must contain 64 hexadecimal characters");
            }
            return new FixturePin(path, packageName, versionName, sha256, revision);
        }

        private static String required(String value, String name) {
            String result = value.strip();
            if (result.isEmpty()) {
                throw new IllegalArgumentException(name + " must not be blank");
            }
            return result;
        }
    }
}
