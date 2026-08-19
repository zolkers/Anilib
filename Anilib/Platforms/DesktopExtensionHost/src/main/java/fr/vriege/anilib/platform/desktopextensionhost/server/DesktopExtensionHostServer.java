package fr.vriege.anilib.platform.desktopextensionhost.server;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import fr.vriege.anilib.platform.desktopextensionhost.extension.ExtensionDownloadClient;
import fr.vriege.anilib.platform.desktopextensionhost.extension.ExtensionRegistry;
import fr.vriege.anilib.platform.desktopextensionhost.extension.InstalledExtension;
import fr.vriege.anilib.platform.desktopextensionhost.protocol.DesktopExtensionHostProtocol;

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

public final class DesktopExtensionHostServer implements AutoCloseable {
    private final Path dataDirectory;
    private final HttpServer server;
    private final ExecutorService executor;
    private final ExtensionRegistry extensionRegistry;
    private final ExtensionDownloadClient downloadClient;

    private DesktopExtensionHostServer(
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

    public static DesktopExtensionHostServer open(InetAddress address, int port, Path dataDirectory) {
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
            DesktopExtensionHostServer result = new DesktopExtensionHostServer(
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
        server.createContext(DesktopExtensionHostProtocol.HEALTH_PATH, exchange -> {
            if (ExtensionHostHttpExchange.requireGet(exchange)) {
                ExtensionHostHttpExchange.json(exchange, 200, healthJson());
            }
        });
        server.createContext(DesktopExtensionHostProtocol.CAPABILITIES_PATH, exchange -> {
            if (ExtensionHostHttpExchange.requireGet(exchange)) {
                ExtensionHostHttpExchange.json(exchange, 200, DesktopExtensionHostCapabilities.bootstrap().toJson());
            }
        });
        server.createContext(DesktopExtensionHostProtocol.SOURCES_PATH, exchange -> safely(exchange, () -> {
            if (ExtensionHostHttpExchange.requireGet(exchange)) {
                ExtensionHostHttpExchange.json(exchange, 200, "{\"manga\":[],\"anime\":[]}");
            }
        }));
        server.createContext(
                DesktopExtensionHostProtocol.INSTALLED_EXTENSIONS_PATH,
                exchange -> safely(exchange, () -> {
                    if (ExtensionHostHttpExchange.requireGet(exchange)) {
                        ExtensionHostHttpExchange.json(
                                exchange, 200, installedJson(extensionRegistry.installed()));
                    }
                }));
        server.createContext(DesktopExtensionHostProtocol.INSTALL_EXTENSION_PATH, exchange -> safely(exchange, () -> {
            if (!ExtensionHostHttpExchange.requirePost(exchange)) {
                return;
            }
            URI uri = URI.create(ExtensionHostHttpExchange.stringField(
                    ExtensionHostHttpExchange.body(exchange), "apk"));
            Path downloaded = downloadClient.download(uri, dataDirectory.resolve("downloads"));
            try {
                InstalledExtension installed = extensionRegistry.install(downloaded);
                ExtensionHostHttpExchange.json(exchange, 200, "{\"ok\":true,\"name\":"
                        + ExtensionHostHttpExchange.jsonString(installed.metadata().displayName())
                        + ",\"pkg\":"
                        + ExtensionHostHttpExchange.jsonString(installed.metadata().packageName()) + '}');
            } finally {
                deleteDownload(downloaded);
            }
        }));
        server.createContext(DesktopExtensionHostProtocol.UNINSTALL_EXTENSION_PATH, exchange -> safely(exchange, () -> {
            if (!ExtensionHostHttpExchange.requirePost(exchange)) {
                return;
            }
            String packageName = ExtensionHostHttpExchange.stringField(
                    ExtensionHostHttpExchange.body(exchange), "pkg");
            boolean removed = extensionRegistry.uninstall(packageName);
            ExtensionHostHttpExchange.json(exchange, removed ? 200 : 404,
                    removed ? "{\"ok\":true}" : "{\"ok\":false,\"error\":\"not_installed\"}");
        }));
        server.createContext(
                DesktopExtensionHostProtocol.EXTENSION_REPOSITORIES_PATH,
                exchange -> safely(exchange, () -> {
                    if (ExtensionHostHttpExchange.requirePost(exchange)) {
                        ExtensionHostHttpExchange.json(exchange, 200, "{\"ok\":true}");
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
        return "{\"status\":\"ok\",\"service\":\"" + DesktopExtensionHostProtocol.SERVICE
                + "\",\"protocolVersion\":" + DesktopExtensionHostProtocol.VERSION + '}';
    }

    private static String installedJson(List<InstalledExtension> extensions) {
        return "{\"extensions\":[" + String.join(",", extensions.stream().map(extension -> {
            var metadata = extension.metadata();
            return "{\"pkg\":" + ExtensionHostHttpExchange.jsonString(metadata.packageName())
                    + ",\"name\":" + ExtensionHostHttpExchange.jsonString(metadata.displayName())
                    + ",\"versionName\":" + ExtensionHostHttpExchange.jsonString(metadata.versionName())
                    + ",\"versionCode\":" + metadata.versionCode()
                    + ",\"kind\":"
                    + ExtensionHostHttpExchange.jsonString(metadata.kind().name().toLowerCase(Locale.ROOT))
                    + ",\"nsfw\":" + metadata.adult() + '}';
        }).toList()) + "]}";
    }

    private static void safely(HttpExchange exchange, Runnable action) {
        try {
            action.run();
        } catch (IllegalArgumentException exception) {
            ExtensionHostHttpExchange.json(exchange, 400, "{\"error\":"
                    + ExtensionHostHttpExchange.jsonString(message(exception)) + '}');
        } catch (RuntimeException exception) {
            ExtensionHostHttpExchange.json(exchange, 500, "{\"error\":"
                    + ExtensionHostHttpExchange.jsonString(message(exception)) + '}');
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
