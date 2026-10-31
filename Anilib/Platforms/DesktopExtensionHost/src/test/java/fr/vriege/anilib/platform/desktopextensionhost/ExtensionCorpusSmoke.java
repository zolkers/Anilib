package fr.vriege.anilib.platform.desktopextensionhost;

import fr.vriege.anilib.platform.desktopextensionhost.extension.ExtensionInstallationException;
import fr.vriege.anilib.platform.desktopextensionhost.extension.ExtensionKind;
import fr.vriege.anilib.platform.desktopextensionhost.extension.ExtensionRegistry;
import fr.vriege.anilib.platform.desktopextensionhost.extension.ExtensionRuntimeCatalog;
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
import java.time.Duration;
import java.util.Arrays;
import java.util.Comparator;

/* Read-only corpus audit: all work and conversions happen on temporary copies. */
public final class ExtensionCorpusSmoke {
    private ExtensionCorpusSmoke() {}

    public static void main(String[] args) throws Exception {
        if (args.length < 1 || args.length > 2)
            throw new IllegalArgumentException("Expected fixture directory [--live]");
        Path fixtures = Path.of(args[0]).toAbsolutePath().normalize();
        boolean live = args.length == 2 && args[1].equals("--live");
        Path scratch = Files.createTempDirectory("anilib-extension-corpus-");
        Path installed = Files.createDirectory(scratch.resolve("extensions"));
        try {
            int count = 0;
            try (var files = Files.list(fixtures)) {
                for (Path file :
                        files.filter(path -> path.toString().endsWith(".apk")).sorted().toList()) {
                    Path jar =
                            file.resolveSibling(
                                    file.getFileName().toString().replaceFirst("\\.apk$", ".jar"));
                    if (Files.isSymbolicLink(file) || Files.isSymbolicLink(jar))
                        throw new IllegalArgumentException("Fixture must not be a link");
                    Files.copy(file, installed.resolve(file.getFileName()));
                    if (Files.isRegularFile(jar))
                        Files.copy(jar, installed.resolve(jar.getFileName()));
                    count++;
                }
            }
            if (count == 0) throw new IllegalArgumentException("No APK fixtures");
            ExtensionRegistry registry = new ExtensionRegistry(scratch);
            registry.prepareInstalledArchives();
            if (registry.installed().size() != count) {
                throw new IllegalStateException("Some fixture APKs could not be prepared");
            }
            try (ExtensionRuntimeCatalog catalog = new ExtensionRuntimeCatalog(registry);
                    DesktopExtensionHostServer server =
                            DesktopExtensionHostServer.open(
                                    InetAddress.getLoopbackAddress(), 0, scratch)) {
                for (var extension : registry.installed()) {
                    catalog.validate(extension);
                    System.out.println("LOAD_OK " + extension.metadata().packageName());
                }
                System.out.println("Corpus: " + count + " real extension packages validated");
                verifyRejectedUpdate(registry, registry.installed().getFirst());
                if (live) {
                    server.start();
                    HttpClient client =
                            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
                    int passed = 0;
                    try (var snapshot = catalog.discover()) {
                        if (!snapshot.failures().isEmpty())
                            throw new IllegalStateException(snapshot.failures().toString());
                        for (var source : snapshot.sources()) {
                            String route =
                                    source.kind() == ExtensionKind.ANIME
                                            ? DesktopExtensionHostProtocol.ANIME_PATH
                                            : DesktopExtensionHostProtocol.MANGA_PATH;
                            URI uri =
                                    URI.create(
                                            "http://127.0.0.1:"
                                                    + server.port()
                                                    + route
                                                    + source.id()
                                                    + "/popular?page=1");
                            try {
                                var response =
                                        client.send(
                                                HttpRequest.newBuilder(uri)
                                                        .timeout(Duration.ofSeconds(45))
                                                        .GET()
                                                        .build(),
                                                HttpResponse.BodyHandlers.ofString());
                                boolean ok =
                                        response.statusCode() == 200
                                                && response.body().contains("\"title\":");
                                if (ok) passed++;
                                System.out.println(
                                        "LIVE "
                                                + source.name()
                                                + " "
                                                + (ok
                                                        ? "CATALOG_OK"
                                                        : "HTTP_" + response.statusCode()));
                            } catch (Exception failure) {
                                System.out.println(
                                        "LIVE "
                                                + source.name()
                                                + " "
                                                + failure.getClass().getSimpleName());
                            }
                        }
                        System.out.println(
                                "Live catalogues: " + passed + '/' + snapshot.sources().size());
                    }
                }
            }
        } finally {
            try (var files = Files.walk(scratch)) {
                for (Path file : files.sorted(Comparator.reverseOrder()).toList())
                    Files.deleteIfExists(file);
            }
        }
    }

    private static void verifyRejectedUpdate(
            ExtensionRegistry registry, InstalledExtension extension) throws Exception {
        byte[] apk = Files.readAllBytes(extension.apk());
        byte[] archive = Files.readAllBytes(extension.archive());
        try {
            registry.install(
                    extension.apk(),
                    staged -> {
                        throw new IllegalArgumentException("Expected preflight rejection");
                    },
                    commit -> {
                        throw new AssertionError("Rejected update reached the commit phase");
                    });
            throw new AssertionError("Rejected update was installed");
        } catch (ExtensionInstallationException expected) {
            if (!expected.getCause().getMessage().equals("Expected preflight rejection"))
                throw expected;
        }
        if (!Arrays.equals(apk, Files.readAllBytes(extension.apk()))
                || !Arrays.equals(archive, Files.readAllBytes(extension.archive()))) {
            throw new AssertionError("Preflight failure replaced the installed APK/JAR pair");
        }
        System.out.println("Rejected update: previous APK/JAR pair preserved");
    }
}
