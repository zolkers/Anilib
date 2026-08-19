package fr.vriege.anilib.platform.desktopengine;

import fr.vriege.anilib.platform.desktopengine.protocol.DesktopEngineProtocol;
import fr.vriege.anilib.platform.desktopengine.server.DesktopEngineServer;

import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

public final class DesktopEngineSmoke {
    private DesktopEngineSmoke() {
    }

    public static void main(String[] arguments) throws Exception {
        Path data = Files.createTempDirectory("anilib-desktop-engine-");
        try (DesktopEngineServer server = DesktopEngineServer.open(
                InetAddress.getLoopbackAddress(), 0, data)) {
            server.start();
            HttpResponse<String> response = HttpClient.newHttpClient().send(
                    HttpRequest.newBuilder(URI.create(
                            "http://127.0.0.1:" + server.port() + DesktopEngineProtocol.HEALTH_PATH)).build(),
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200
                    || !response.body().contains("\"service\":\"" + DesktopEngineProtocol.SERVICE + "\"")) {
                throw new IllegalStateException("Desktop engine health protocol failed: " + response);
            }
        } finally {
            try (var paths = Files.walk(data)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(path);
                }
            }
        }
    }
}
