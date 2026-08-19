package fr.vriege.anilib.platform.desktopextensionhost;

import fr.vriege.anilib.platform.desktopextensionhost.server.DesktopExtensionHostServer;

import java.net.InetAddress;
import java.nio.file.Path;

public final class EmbeddedDesktopExtensionHost implements AutoCloseable {
    private final DesktopExtensionHostServer server;

    private EmbeddedDesktopExtensionHost(DesktopExtensionHostServer server) {
        this.server = server;
    }

    public static EmbeddedDesktopExtensionHost start(Path dataDirectory) {
        DesktopExtensionHostServer server = DesktopExtensionHostServer.open(
                InetAddress.getLoopbackAddress(), 0, dataDirectory);
        server.start();
        return new EmbeddedDesktopExtensionHost(server);
    }

    public int port() {
        return server.port();
    }

    @Override
    public void close() {
        server.close();
    }
}
