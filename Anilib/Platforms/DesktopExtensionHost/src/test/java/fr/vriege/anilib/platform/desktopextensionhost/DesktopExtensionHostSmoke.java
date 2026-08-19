package fr.vriege.anilib.platform.desktopextensionhost;

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

public final class DesktopExtensionHostSmoke {
    private DesktopExtensionHostSmoke() {
    }

    public static void main(String[] arguments) throws Exception {
        ExtensionRelocationSmoke.verify();
        ExtensionRuntimeCatalogSmoke.verify();
        Path data = Files.createTempDirectory("anilib-desktop-extension-host-");
        try (DesktopExtensionHostServer server = DesktopExtensionHostServer.open(
                InetAddress.getLoopbackAddress(), 0, data)) {
            server.start();
            HttpResponse<String> response = HttpClient.newHttpClient().send(
                    HttpRequest.newBuilder(URI.create(
                            "http://127.0.0.1:" + server.port() + DesktopExtensionHostProtocol.HEALTH_PATH)).build(),
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200
                    || !response.body().contains("\"service\":\"" + DesktopExtensionHostProtocol.SERVICE + "\"")) {
                throw new IllegalStateException("Desktop engine health protocol failed: " + response);
            }
            verifyGet(server, DesktopExtensionHostProtocol.INSTALLED_EXTENSIONS_PATH, "\"extensions\":[]");
            verifyGet(server, DesktopExtensionHostProtocol.SOURCES_PATH, "\"anime\":[]");
            verifyGet(server, DesktopExtensionHostProtocol.CAPABILITIES_PATH, "\"sourceCatalogue\":true");
            HttpResponse<String> rejected = HttpClient.newHttpClient().send(
                    HttpRequest.newBuilder(endpoint(server, DesktopExtensionHostProtocol.UNINSTALL_EXTENSION_PATH))
                            .header("content-type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString("not-json"))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            if (rejected.statusCode() != 400 || !rejected.body().contains("error")) {
                throw new IllegalStateException("Desktop engine did not isolate a malformed request");
            }
            verifyGet(server, DesktopExtensionHostProtocol.HEALTH_PATH, "\"status\":\"ok\"");
        } finally {
            try (var paths = Files.walk(data)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(path);
                }
            }
        }
    }

    private static void verifyGet(DesktopExtensionHostServer server, String path, String expected) throws Exception {
        HttpResponse<String> response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(endpoint(server, path)).build(),
                HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200 || !response.body().contains(expected)) {
            throw new IllegalStateException("Desktop engine endpoint failed: " + path + ' ' + response);
        }
    }

    private static URI endpoint(DesktopExtensionHostServer server, String path) {
        return URI.create("http://127.0.0.1:" + server.port() + path);
    }
}
