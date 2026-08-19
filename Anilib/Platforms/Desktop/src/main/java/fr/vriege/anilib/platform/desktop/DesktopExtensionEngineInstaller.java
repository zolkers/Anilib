package fr.vriege.anilib.platform.desktop;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;

final class DesktopExtensionEngineInstaller {
    private static final String VERSION = "0.2.9";
    private static final URI DOWNLOAD = URI.create(
            "https://github.com/miwayomi/miwayomi/releases/download/v0.2.9/miwayomi-all.jar");
    private static final String SHA_256 = "475b95dabaaca9f283263a5eafa12bec3580b658caae70407ae227ae4fa0e9b7";
    private static final long SIZE_BYTES = 102_140_165L;
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration DOWNLOAD_TIMEOUT = Duration.ofMinutes(5);

    private DesktopExtensionEngineInstaller() {
    }

    static void install(Path engineDirectory) {
        Path directory = engineDirectory.toAbsolutePath().normalize();
        Path jar = directory.resolve("miwayomi-" + VERSION + "-all.jar");
        Path configuration = directory.resolve("engine.properties");
        requireManagedTarget(directory, jar);
        requireManagedTarget(directory, configuration);
        try {
            Files.createDirectories(directory);
            if (!validJar(jar)) {
                download(directory, jar);
            }
            writeConfiguration(directory, jar, configuration);
        } catch (IOException exception) {
            throw new UncheckedIOException("Unable to install the desktop compatibility engine", exception);
        }
    }

    private static void download(Path directory, Path jar) throws IOException {
        Path temporary = Files.createTempFile(directory, ".miwayomi-", ".part");
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(CONNECT_TIMEOUT)
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build();
            HttpRequest request = HttpRequest.newBuilder(DOWNLOAD)
                    .timeout(DOWNLOAD_TIMEOUT)
                    .header("User-Agent", "Anilib-Desktop-Compatibility")
                    .GET()
                    .build();
            HttpResponse<InputStream> response;
            try {
                response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Desktop compatibility download was interrupted", exception);
            }
            if (response.statusCode() != 200 || !"https".equalsIgnoreCase(response.uri().getScheme())) {
                response.body().close();
                throw new IOException("Desktop compatibility download returned HTTP " + response.statusCode());
            }
            try (InputStream input = response.body(); var output = Files.newOutputStream(temporary)) {
                byte[] buffer = new byte[64 * 1024];
                long total = 0;
                int count;
                while ((count = input.read(buffer)) >= 0) {
                    total = Math.addExact(total, count);
                    if (total > SIZE_BYTES) {
                        throw new IOException("Desktop compatibility download exceeds its signed size");
                    }
                    output.write(buffer, 0, count);
                }
                if (total != SIZE_BYTES) {
                    throw new IOException("Desktop compatibility download is incomplete");
                }
            }
            if (!SHA_256.equals(sha256(temporary))) {
                throw new SecurityException("Desktop compatibility download failed SHA-256 verification");
            }
            replace(temporary, jar);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static void writeConfiguration(Path directory, Path jar, Path configuration) throws IOException {
        Path temporary = Files.createTempFile(directory, ".engine-", ".properties");
        try {
            String content = "enabled=true\n"
                    + "jar=" + jar.getFileName() + "\n"
                    + "sha256=" + SHA_256 + "\n";
            Files.writeString(temporary, content, StandardCharsets.UTF_8);
            replace(temporary, configuration);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static boolean validJar(Path jar) {
        return Files.isRegularFile(jar, LinkOption.NOFOLLOW_LINKS)
                && !Files.isSymbolicLink(jar)
                && size(jar) == SIZE_BYTES
                && SHA_256.equals(sha256(jar));
    }

    private static long size(Path file) {
        try {
            return Files.size(file);
        } catch (IOException exception) {
            throw new UncheckedIOException("Unable to inspect desktop compatibility engine size", exception);
        }
    }

    private static String sha256(Path file) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = Files.newInputStream(file)) {
                byte[] buffer = new byte[64 * 1024];
                int count;
                while ((count = input.read(buffer)) >= 0) {
                    digest.update(buffer, 0, count);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JDK does not provide SHA-256", exception);
        } catch (IOException exception) {
            throw new UncheckedIOException("Unable to verify desktop compatibility engine", exception);
        }
    }

    private static void replace(Path source, Path destination) throws IOException {
        try {
            Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void requireManagedTarget(Path directory, Path target) {
        if (target.getParent() == null || !target.getParent().equals(directory)) {
            throw new IllegalArgumentException("Desktop compatibility target escaped its managed directory");
        }
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)
                && (!Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(target))) {
            throw new IllegalArgumentException("Desktop compatibility target is not a regular non-link file");
        }
    }
}
