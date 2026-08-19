package fr.vriege.anilib.platform.desktop;

import fr.vriege.anilib.feature.extensionrepository.ExtensionArtifactFormat;
import fr.vriege.anilib.feature.extensionrepository.ExtensionPackageMetadata;
import fr.vriege.anilib.feature.extensionrepository.runtime.FileExtensionRepositoryStore;
import fr.vriege.anilib.feature.extensionrepository.runtime.ExtensionRepositoryLocations;
import fr.vriege.anilib.feature.extensionrepository.runtime.MiwayomiSourceBridge;
import fr.vriege.anilib.feature.extensionrepository.ui.ApkExtensionPlatform;
import fr.vriege.anilib.framework.http.AnilibHttpClient;
import fr.vriege.anilib.framework.http.HttpTransport;
import fr.vriege.anilib.kernel.AnilibPlugin;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.ServerSocket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

final class DesktopExtensionEngine implements ApkExtensionPlatform, AutoCloseable {
    private static final Pattern SHA_256 = Pattern.compile("[0-9a-f]{64}");
    private static final Duration START_TIMEOUT = Duration.ofSeconds(30);
    private static final int START_ATTEMPTS = 3;

    private final MiwayomiSourceBridge bridge;
    private final Process process;
    private final List<AnilibPlugin> sourceBundles;
    private final String diagnostic;

    private DesktopExtensionEngine(
            MiwayomiSourceBridge bridge,
            Process process,
            List<AnilibPlugin> sourceBundles,
            String diagnostic) {
        this.bridge = bridge;
        this.process = process;
        this.sourceBundles = List.copyOf(sourceBundles);
        this.diagnostic = diagnostic;
    }

    static DesktopExtensionEngine open(Path dataDirectory, HttpTransport transport) {
        Path data = Objects.requireNonNull(dataDirectory, "dataDirectory").toAbsolutePath().normalize();
        Path engineDirectory = data.resolve("extension-engine");
        Path configuration = engineDirectory.resolve("engine.properties");
        Properties overrides = systemOverrides();
        if (overrides.isEmpty() && !Files.isRegularFile(configuration, LinkOption.NOFOLLOW_LINKS)) {
            return unavailable(
                    "Desktop APK engine is not configured. See extension-engine/README.txt in the Anilib data folder.",
                    engineDirectory);
        }
        try {
            Properties properties = overrides.isEmpty() ? load(configuration) : overrides;
            if (!Boolean.parseBoolean(properties.getProperty("enabled", "false"))) {
                return unavailable("Desktop APK engine is disabled in engine.properties.", engineDirectory);
            }
            Path approvedJar = approvedJar(engineDirectory, properties);
            String expectedSha256 = expectedSha256(properties);
            requireChecksum(approvedJar, expectedSha256);
            Path runtimeJar = prepareRuntimeCopy(engineDirectory, approvedJar, expectedSha256);
            return start(data, engineDirectory, runtimeJar, transport);
        } catch (RuntimeException exception) {
            return unavailable("Desktop APK engine refused to start: " + exception.getMessage(), engineDirectory);
        }
    }

    List<AnilibPlugin> sourceBundles() {
        return sourceBundles;
    }

    @Override
    public boolean available() {
        return bridge != null && process != null && process.isAlive();
    }

    @Override
    public String availabilityDescription() {
        return available()
                ? "Existing manga and anime APK extensions run in Anilib's isolated desktop engine. "
                        + "Newly installed sources activate after restart."
                : diagnostic;
    }

    @Override
    public String installActionLabel() {
        return "Install for desktop";
    }

    @Override
    public String installProgressLabel() {
        return "Installing APK in desktop engine";
    }

    @Override
    public CompletableFuture<String> install(ExtensionPackageMetadata extensionPackage) {
        if (!available()) {
            return CompletableFuture.failedFuture(new IllegalStateException(diagnostic));
        }
        return extensionPackage.artifacts().stream()
                .filter(artifact -> artifact.format() == ExtensionArtifactFormat.ANIYOMI_APK)
                .findFirst()
                .map(artifact -> CompletableFuture.supplyAsync(() -> bridge.install(artifact.uri())))
                .orElseGet(() -> CompletableFuture.failedFuture(
                        new IllegalArgumentException("Extension does not publish an APK artifact")));
    }

    @Override
    public void close() {
        if (process == null || !process.isAlive()) {
            return;
        }
        process.destroy();
        try {
            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                process.waitFor(5, TimeUnit.SECONDS);
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
        }
    }

    private static DesktopExtensionEngine start(
            Path dataDirectory,
            Path engineDirectory,
            Path runtimeJar,
            HttpTransport transport) {
        RuntimeException failure = null;
        for (int attempt = 0; attempt < START_ATTEMPTS; attempt++) {
            int port = freePort();
            Process process = null;
            try {
                process = launch(engineDirectory, runtimeJar, port);
                AnilibHttpClient client = request -> transport.exchange(request, request.headers());
                MiwayomiSourceBridge bridge = new MiwayomiSourceBridge(
                        URI.create("http://127.0.0.1:" + port + "/"),
                        client);
                awaitHealthy(bridge, process);
                List<URI> repositories = new FileExtensionRepositoryStore(
                        dataDirectory.resolve("extension-repositories.txt")).load().stream()
                        .map(ExtensionRepositoryLocations::indexCandidates)
                        .map(List::getFirst)
                        .toList();
                bridge.saveRepositories(repositories);
                List<AnilibPlugin> sources = bridge.sourceBundles();
                return new DesktopExtensionEngine(
                        bridge,
                        process,
                        sources,
                        "Desktop APK engine is running with " + sources.size() + " source bundles.");
            } catch (RuntimeException exception) {
                failure = exception;
                if (process != null && process.isAlive()) {
                    process.destroyForcibly();
                }
            }
        }
        throw new IllegalStateException("Extension engine did not become ready", failure);
    }

    private static Process launch(Path engineDirectory, Path runtimeJar, int port) {
        Path java = javaExecutable();
        Path engineData = engineDirectory.resolve("data");
        Path log = engineDirectory.resolve("engine.log");
        try {
            Files.createDirectories(engineData);
            Files.writeString(log, "", StandardCharsets.UTF_8);
            ProcessBuilder builder = new ProcessBuilder(
                    java.toString(),
                    "-jar",
                    runtimeJar.toString(),
                    "--host",
                    "127.0.0.1",
                    "--port",
                    Integer.toString(port),
                    "--data",
                    engineData.toString(),
                    "--no-open");
            builder.directory(engineDirectory.toFile());
            builder.redirectErrorStream(true);
            builder.redirectOutput(ProcessBuilder.Redirect.appendTo(log.toFile()));
            sanitizeEnvironment(builder);
            return builder.start();
        } catch (IOException exception) {
            throw new UncheckedIOException("Unable to launch desktop APK engine", exception);
        }
    }

    private static void awaitHealthy(MiwayomiSourceBridge bridge, Process process) {
        long deadline = System.nanoTime() + START_TIMEOUT.toNanos();
        RuntimeException failure = null;
        while (System.nanoTime() < deadline) {
            if (!process.isAlive()) {
                throw new IllegalStateException("Extension engine exited with code " + process.exitValue(), failure);
            }
            try {
                bridge.requireHealthy();
                return;
            } catch (RuntimeException exception) {
                failure = exception;
            }
            try {
                Thread.sleep(150);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while starting extension engine", exception);
            }
        }
        throw new IllegalStateException("Timed out waiting for extension engine health", failure);
    }

    private static Path approvedJar(Path engineDirectory, Properties properties) {
        String configured = requireProperty(properties, "jar");
        Path value = Path.of(configured);
        Path resolved = (value.isAbsolute() ? value : engineDirectory.resolve(value)).toAbsolutePath().normalize();
        if (!Files.isRegularFile(resolved, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(resolved)) {
            throw new IllegalArgumentException("Configured engine JAR is not a regular non-link file");
        }
        return resolved;
    }

    private static Path prepareRuntimeCopy(Path engineDirectory, Path approvedJar, String expectedSha256) {
        Path runtimeDirectory = engineDirectory.resolve("runtime");
        Path runtimeJar = runtimeDirectory.resolve("engine-runtime.jar");
        try {
            Files.createDirectories(runtimeDirectory);
            boolean unsafeTarget = Files.exists(runtimeJar, LinkOption.NOFOLLOW_LINKS)
                    && (!Files.isRegularFile(runtimeJar, LinkOption.NOFOLLOW_LINKS)
                    || Files.isSymbolicLink(runtimeJar));
            if (unsafeTarget) {
                throw new IllegalStateException("Runtime engine target is not a regular non-link file");
            }
            Files.copy(approvedJar, runtimeJar, StandardCopyOption.REPLACE_EXISTING);
            requireChecksum(runtimeJar, expectedSha256);
            return runtimeJar;
        } catch (IOException exception) {
            throw new UncheckedIOException("Unable to prepare isolated engine copy", exception);
        }
    }

    private static String expectedSha256(Properties properties) {
        String value = requireProperty(properties, "sha256").toLowerCase(Locale.ROOT);
        if (!SHA_256.matcher(value).matches()) {
            throw new IllegalArgumentException("sha256 must contain exactly 64 hexadecimal characters");
        }
        return value;
    }

    private static void requireChecksum(Path file, String expected) {
        String actual = sha256(file);
        if (!actual.equals(expected)) {
            throw new SecurityException("Configured engine JAR SHA-256 does not match engine.properties");
        }
    }

    private static String sha256(Path file) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = Files.newInputStream(file)) {
                byte[] buffer = new byte[8192];
                int count;
                while ((count = input.read(buffer)) >= 0) {
                    digest.update(buffer, 0, count);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JDK does not provide SHA-256", exception);
        } catch (IOException exception) {
            throw new UncheckedIOException("Unable to hash configured engine JAR", exception);
        }
    }

    private static Properties load(Path configuration) {
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(configuration)) {
            properties.load(input);
            return properties;
        } catch (IOException exception) {
            throw new UncheckedIOException("Unable to read engine.properties", exception);
        }
    }

    private static Properties systemOverrides() {
        String jar = System.getProperty("anilib.extensionEngine.jar");
        String sha256 = System.getProperty("anilib.extensionEngine.sha256");
        if ((jar == null || jar.isBlank()) && (sha256 == null || sha256.isBlank())) {
            return new Properties();
        }
        if (jar == null || jar.isBlank() || sha256 == null || sha256.isBlank()) {
            throw new IllegalArgumentException(
                    "anilib.extensionEngine.jar and anilib.extensionEngine.sha256 must be set together");
        }
        Properties properties = new Properties();
        properties.setProperty("enabled", "true");
        properties.setProperty("jar", jar);
        properties.setProperty("sha256", sha256);
        return properties;
    }

    private static String requireProperty(Properties properties, String name) {
        String value = properties.getProperty(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("engine.properties is missing " + name);
        }
        return value.strip();
    }

    private static Path javaExecutable() {
        boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
        Path executable = Path.of(System.getProperty("java.home"), "bin", windows ? "java.exe" : "java")
                .toAbsolutePath()
                .normalize();
        if (!Files.isRegularFile(executable, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalStateException("Java executable is unavailable under java.home");
        }
        return executable;
    }

    private static int freePort() {
        try (ServerSocket socket = new ServerSocket(0, 1, java.net.InetAddress.getByName("127.0.0.1"))) {
            return socket.getLocalPort();
        } catch (IOException exception) {
            throw new UncheckedIOException("Unable to reserve a loopback port", exception);
        }
    }

    private static void sanitizeEnvironment(ProcessBuilder builder) {
        List<String> retained = List.of("SystemRoot", "WINDIR", "TEMP", "TMP", "LANG", "LC_ALL");
        var environment = builder.environment();
        var copy = new java.util.HashMap<String, String>();
        for (String name : retained) {
            String value = environment.get(name);
            if (value != null) {
                copy.put(name, value);
            }
        }
        environment.clear();
        environment.putAll(copy);
    }

    private static DesktopExtensionEngine unavailable(String diagnostic, Path engineDirectory) {
        writeInstructions(engineDirectory);
        return new DesktopExtensionEngine(null, null, List.of(), diagnostic);
    }

    private static void writeInstructions(Path engineDirectory) {
        Path instructions = engineDirectory.resolve("README.txt");
        if (Files.exists(instructions, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        String content = """
                Anilib desktop APK engine

                1. Download miwayomi-all.jar from https://github.com/miwayomi/miwayomi/releases
                2. Put it in this folder.
                3. Calculate its SHA-256.
                4. Create engine.properties here with:

                enabled=true
                jar=miwayomi-all.jar
                sha256=<64 lowercase hexadecimal characters>

                Anilib verifies the approved JAR before every launch and runs an isolated copy on 127.0.0.1.
                """;
        try {
            Files.createDirectories(engineDirectory);
            Files.writeString(instructions, content, StandardCharsets.UTF_8);
        } catch (IOException ignored) {
        }
    }
}
