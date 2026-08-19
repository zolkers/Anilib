package fr.vriege.anilib.platform.desktopengine.server;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import fr.vriege.anilib.platform.desktopengine.extension.ExtensionDownloadClient;
import fr.vriege.anilib.platform.desktopengine.extension.ExtensionRegistry;
import fr.vriege.anilib.platform.desktopengine.extension.InstalledExtension;
import fr.vriege.anilib.platform.desktopengine.protocol.DesktopEngineProtocol;

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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class DesktopEngineServer implements AutoCloseable {
    private final Path dataDirectory;
    private final HttpServer server;
    private final ExecutorService executor;
    private final ExtensionRegistry extensionRegistry;
    private final ExtensionDownloadClient downloadClient;

    private DesktopEngineServer(
            Path dataDirectory,
            HttpServer server,
            ExecutorService executor,
            ExtensionRegistry extensionRegistry,
            ExtensionDownloadClient downloadClient) {
        this.dataDirectory = dataDirectory;
        this.server = server;
        this.executor = executor;
        this.extensionRegistry = extensionRegistry;
        this.downloadClient = downloadClient;
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
            DesktopEngineServer result = new DesktopEngineServer(
                    data, server, executor, new ExtensionRegistry(data), new ExtensionDownloadClient());
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
        server.createContext(DesktopEngineProtocol.SOURCES_PATH, exchange -> safely(exchange, () -> {
            if (EngineHttpExchange.requireGet(exchange)) {
                EngineHttpExchange.json(exchange, 200, "{\"manga\":[],\"anime\":[]}");
            }
        }));
        server.createContext(DesktopEngineProtocol.INSTALLED_EXTENSIONS_PATH, exchange -> safely(exchange, () -> {
            if (EngineHttpExchange.requireGet(exchange)) {
                EngineHttpExchange.json(exchange, 200, installedJson(extensionRegistry.installed()));
            }
        }));
        server.createContext(DesktopEngineProtocol.INSTALL_EXTENSION_PATH, exchange -> safely(exchange, () -> {
            if (!EngineHttpExchange.requirePost(exchange)) {
                return;
            }
            URI uri = URI.create(EngineHttpExchange.stringField(EngineHttpExchange.body(exchange), "apk"));
            Path downloaded = downloadClient.download(uri, dataDirectory.resolve("downloads"));
            try {
                InstalledExtension installed = extensionRegistry.install(downloaded);
                EngineHttpExchange.json(exchange, 200, "{\"ok\":true,\"name\":"
                        + EngineHttpExchange.jsonString(installed.metadata().displayName()) + ",\"pkg\":"
                        + EngineHttpExchange.jsonString(installed.metadata().packageName()) + '}');
            } finally {
                deleteDownload(downloaded);
            }
        }));
        server.createContext(DesktopEngineProtocol.UNINSTALL_EXTENSION_PATH, exchange -> safely(exchange, () -> {
            if (!EngineHttpExchange.requirePost(exchange)) {
                return;
            }
            String packageName = EngineHttpExchange.stringField(EngineHttpExchange.body(exchange), "pkg");
            boolean removed = extensionRegistry.uninstall(packageName);
            EngineHttpExchange.json(exchange, removed ? 200 : 404,
                    removed ? "{\"ok\":true}" : "{\"ok\":false,\"error\":\"not_installed\"}");
        }));
        server.createContext(DesktopEngineProtocol.EXTENSION_REPOSITORIES_PATH, exchange -> safely(exchange, () -> {
            if (EngineHttpExchange.requirePost(exchange)) {
                EngineHttpExchange.json(exchange, 200, "{\"ok\":true}");
            }
        }));
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

    private static String installedJson(List<InstalledExtension> extensions) {
        return "{\"extensions\":[" + String.join(",", extensions.stream().map(extension -> {
            var metadata = extension.metadata();
            return "{\"pkg\":" + EngineHttpExchange.jsonString(metadata.packageName())
                    + ",\"name\":" + EngineHttpExchange.jsonString(metadata.displayName())
                    + ",\"versionName\":" + EngineHttpExchange.jsonString(metadata.versionName())
                    + ",\"versionCode\":" + metadata.versionCode()
                    + ",\"kind\":"
                    + EngineHttpExchange.jsonString(metadata.kind().name().toLowerCase(Locale.ROOT))
                    + ",\"nsfw\":" + metadata.adult() + '}';
        }).toList()) + "]}";
    }

    private static void safely(HttpExchange exchange, Runnable action) {
        try {
            action.run();
        } catch (IllegalArgumentException exception) {
            EngineHttpExchange.json(exchange, 400, "{\"error\":"
                    + EngineHttpExchange.jsonString(message(exception)) + '}');
        } catch (RuntimeException exception) {
            EngineHttpExchange.json(exchange, 500, "{\"error\":"
                    + EngineHttpExchange.jsonString(message(exception)) + '}');
        }
    }

    private static String message(RuntimeException exception) {
        return exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
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
    }
}
