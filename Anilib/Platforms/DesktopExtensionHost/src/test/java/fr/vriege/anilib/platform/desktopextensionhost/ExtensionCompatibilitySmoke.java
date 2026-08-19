package fr.vriege.anilib.platform.desktopextensionhost;

import fr.vriege.anilib.platform.desktopextensionhost.extension.ExtensionRegistry;
import fr.vriege.anilib.platform.desktopextensionhost.protocol.DesktopExtensionHostProtocol;
import fr.vriege.anilib.platform.desktopextensionhost.server.DesktopExtensionHostServer;

import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ExtensionCompatibilitySmoke {
    private ExtensionCompatibilitySmoke() {
    }

    public static void main(String[] arguments) throws Exception {
        if (arguments.length != 2) {
            throw new IllegalArgumentException("Expected manga and anime extension APK paths");
        }
        Path data = Files.createTempDirectory("anilib-extension-compatibility-");
        try {
            ExtensionRegistry registry = new ExtensionRegistry(data);
            registry.install(Path.of(arguments[0]));
            registry.install(Path.of(arguments[1]));
            try (DesktopExtensionHostServer server = DesktopExtensionHostServer.open(
                    InetAddress.getLoopbackAddress(), 0, data)) {
                server.start();
                String sources = get(server, DesktopExtensionHostProtocol.SOURCES_PATH);
                String mangaId = sourceId(sources, "MangaDex", "fr");
                String animeId = sourceId(sources, "Anime-Sama", "fr");
                verifyCatalogue(get(server, DesktopExtensionHostProtocol.MANGA_PATH + mangaId + "/popular?page=1"),
                        "mangas");
                verifyCatalogue(get(server, DesktopExtensionHostProtocol.ANIME_PATH
                                + animeId + "/search?page=1&query=one%20piece"),
                        "animes");
            }
        } finally {
            try (var paths = Files.walk(data)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(path);
                }
            }
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

    private static String sourceId(String document, String name, String language) {
        Pattern pattern = Pattern.compile("\\{\\\"id\\\":\\\"([0-9]+)\\\",\\\"name\\\":\\\""
                + Pattern.quote(name) + "\\\",\\\"lang\\\":\\\"" + Pattern.quote(language) + "\\\"");
        Matcher matcher = pattern.matcher(document);
        if (!matcher.find()) {
            throw new IllegalStateException("Expected source is unavailable: " + name + " (" + language + ')');
        }
        return matcher.group(1);
    }

    private static void verifyCatalogue(String document, String field) {
        if (!document.contains("\"" + field + "\":[{") || !document.contains("\"title\":")) {
            throw new IllegalStateException("Extension catalogue is empty or malformed: " + document);
        }
    }
}
