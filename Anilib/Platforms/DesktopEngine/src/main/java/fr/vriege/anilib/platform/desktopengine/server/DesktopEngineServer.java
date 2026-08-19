package fr.vriege.anilib.platform.desktopengine.server;

import com.sun.net.httpserver.HttpServer;
import fr.vriege.anilib.platform.desktopengine.protocol.DesktopEngineProtocol;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class DesktopEngineServer implements AutoCloseable {
    private final Path dataDirectory;
    private final HttpServer server;
    private final ExecutorService executor;

    private DesktopEngineServer(Path dataDirectory, HttpServer server, ExecutorService executor) {
        this.dataDirectory = dataDirectory;
        this.server = server;
        this.executor = executor;
    }

    public static DesktopEngineServer open(InetAddress address, int port, Path dataDirectory) {
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
            DesktopEngineServer result = new DesktopEngineServer(data, server, executor);
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
        server.createContext(DesktopEngineProtocol.HEALTH_PATH, exchange -> {
            if (EngineHttpExchange.requireGet(exchange)) {
                EngineHttpExchange.json(exchange, 200, healthJson());
            }
        });
        server.createContext(DesktopEngineProtocol.CAPABILITIES_PATH, exchange -> {
            if (EngineHttpExchange.requireGet(exchange)) {
                EngineHttpExchange.json(exchange, 200, DesktopEngineCapabilities.bootstrap().toJson());
            }
        });
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
        return "{\"status\":\"ok\",\"service\":\"" + DesktopEngineProtocol.SERVICE
                + "\",\"protocolVersion\":" + DesktopEngineProtocol.VERSION + '}';
    }

    @Override
    public void close() {
        server.stop(0);
        executor.close();
    }
}
