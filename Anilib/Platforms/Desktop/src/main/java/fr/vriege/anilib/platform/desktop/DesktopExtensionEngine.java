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
import fr.vriege.anilib.kernel.PluginRegistration;
import fr.vriege.anilib.kernel.StartedAnilib;
import fr.vriege.anilib.foundation.component.ComponentId;

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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
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
    private static final String RUNTIME_STATE_FILE = "process.properties";

    private final Path dataDirectory;
    private final Path engineDirectory;
    private final HttpTransport transport;
    private volatile DesktopApkEngineClient bridge;
    private volatile ProcessHandle process;
    private final List<AnilibPlugin> sourceBundles;
    private final Map<String, List<PluginRegistration>> dynamicRegistrations = new LinkedHashMap<>();
    private final Set<String> installedPackageNames = new LinkedHashSet<>();
    private volatile StartedAnilib started;
    private volatile String diagnostic;

    private DesktopExtensionEngine(
            Path dataDirectory,
            Path engineDirectory,
            HttpTransport transport,
            DesktopApkEngineClient bridge,
            ProcessHandle process,
            List<AnilibPlugin> sourceBundles,
            Set<String> installedPackages,
            String diagnostic) {
        this.dataDirectory = dataDirectory;
        this.engineDirectory = engineDirectory;
        this.transport = transport;
        this.bridge = bridge;
        this.process = process;
        this.sourceBundles = List.copyOf(sourceBundles);
        this.installedPackageNames.addAll(installedPackages);
        this.diagnostic = diagnostic;
    }

    static DesktopExtensionEngine open(Path dataDirectory, HttpTransport transport) {
        Path data = Objects.requireNonNull(dataDirectory, "dataDirectory").toAbsolutePath().normalize();
        Path engineDirectory = data.resolve("extension-engine");
        Path configuration = engineDirectory.resolve("engine.properties");
        Properties overrides = systemOverrides();
        if (overrides.isEmpty() && !Files.isRegularFile(configuration, LinkOption.NOFOLLOW_LINKS)) {
            return unavailable(
                    data,
                    transport,
                    "Desktop APK compatibility is ready to install. Select Install for desktop on any APK source.",
                    engineDirectory);
        }
        try {
            Properties properties = overrides.isEmpty() ? load(configuration) : overrides;
            if (!Boolean.parseBoolean(properties.getProperty("enabled", "false"))) {
                return unavailable(
                        data,
                        transport,
                        "Desktop APK engine is disabled in engine.properties.",
                        engineDirectory);
            }
            Path approvedJar = approvedJar(engineDirectory, properties);
            String expectedSha256 = expectedSha256(properties);
            requireChecksum(approvedJar, expectedSha256);
            DesktopExtensionEngine running = resume(data, engineDirectory, transport);
            if (running != null) {
                return running;
            }
            Path runtimeJar = prepareRuntimeCopy(engineDirectory, approvedJar, expectedSha256);
            repairConvertedExtensions(engineDirectory, runtimeJar);
            return start(data, engineDirectory, runtimeJar, transport);
        } catch (RuntimeException exception) {
            return unavailable(
                    data,
                    transport,
                    "Desktop APK engine refused to start: " + exception.getMessage(),
                    engineDirectory);
        }
    }

    List<AnilibPlugin> sourceBundles() {
        return sourceBundles;
    }

    synchronized void attach(StartedAnilib product) {
        started = Objects.requireNonNull(product, "product must not be null");
        sourceBundles.forEach(bundle -> registerSource(product, bundle));
    }

    @Override
    public synchronized Set<String> installedPackageNames() {
        return Set.copyOf(installedPackageNames);
    }

    @Override
    public synchronized Set<String> activePackageNames() {
        return Set.copyOf(dynamicRegistrations.keySet());
    }

    @Override
    public boolean available() {
        return bridge != null && process != null && process.isAlive();
    }

    @Override
    public boolean installationSupported() {
        return true;
    }

    @Override
    public String availabilityDescription() {
        return available()
                ? "Existing manga and anime APK extensions run in Anilib's isolated desktop engine. "
                        + "Newly installed sources activate immediately."
                : diagnostic;
    }

    @Override
    public String installActionLabel() {
        return "Install for desktop";
    }

    @Override
    public String installProgressLabel() {
        return available()
                ? "Installing APK in desktop engine"
                : "Downloading verified desktop compatibility";
    }

    @Override
    public CompletableFuture<String> install(ExtensionPackageMetadata extensionPackage) {
        URI artifact = extensionPackage.artifacts().stream()
                .filter(candidate -> candidate.format() == ExtensionArtifactFormat.ANIYOMI_APK)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Extension does not publish an APK artifact"))
                .uri();
        return CompletableFuture.supplyAsync(() -> {
            ensureAvailable();
            bridge.install(artifact);
            installedPackageNames.add(extensionPackage.packageName());
            int activated = activateNewSources();
            if (activated == 0) {
                Path convertedJar = convertedExtensionJar(artifact);
                int repairs = repairWhileStopped(convertedJar);
                if (repairs > 0) {
                    activated = activateNewSources();
                }
            }
            if (activated == 0) {
                throw new IllegalStateException(
                        extensionPackage.displayName() + " is installed, but this APK generation is not yet "
                                + "compatible with the current desktop engine. The APK was kept so a future "
                                + "engine update can activate it.");
            }
            return extensionPackage.displayName() + " installed. " + activated
                    + " source(s) added immediately to Browse.";
        });
    }

    @Override
    public boolean uninstallationSupported() {
        return true;
    }

    @Override
    public CompletableFuture<String> uninstall(String packageName) {
        return CompletableFuture.supplyAsync(() -> {
            ensureAvailable();
            String message = bridge.uninstall(packageName);
            deactivate(packageName);
            return message + " Its sources were removed from Browse.";
        });
    }

    private synchronized int activateNewSources() {
        StartedAnilib product = started;
        if (product == null) {
            throw new IllegalStateException("Anilib product is not attached to the desktop extension engine");
        }
        Set<ComponentId> active = product.components().stream()
                .map(component -> component.id())
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        int count = 0;
        for (AnilibPlugin bundle : bridge.sourceBundles()) {
            if (active.add(bundle.manifest().descriptor().id())) {
                registerSource(product, bundle);
                count++;
            }
        }
        return count;
    }

    private void registerSource(StartedAnilib product, AnilibPlugin bundle) {
        String packageName = bundle.manifest().descriptor().version();
        dynamicRegistrations.computeIfAbsent(packageName, ignored -> new ArrayList<>())
                .add(product.install(bundle));
    }

    private synchronized void deactivate(String packageName) {
        installedPackageNames.remove(packageName);
        List<PluginRegistration> registrations = dynamicRegistrations.remove(packageName);
        if (registrations != null) {
            registrations.reversed().forEach(registration -> {
                try {
                    registration.close();
                } catch (RuntimeException ignored) {
                }
            });
        }
    }

    @Override
    public void close() {
        stopProcess(process);
    }

    private void stopProcess(ProcessHandle running) {
        if (running == null || !running.isAlive()) {
            clearRuntimeState(engineDirectory, running);
            return;
        }
        running.destroy();
        try {
            running.onExit().get(5, TimeUnit.SECONDS);
        } catch (java.util.concurrent.TimeoutException exception) {
            if (running.isAlive()) {
                running.destroyForcibly();
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            running.destroyForcibly();
        } catch (java.util.concurrent.ExecutionException exception) {
            running.destroyForcibly();
        } finally {
            clearRuntimeState(engineDirectory, running);
        }
    }

    private synchronized void ensureAvailable() {
        if (available()) {
            return;
        }
        Path configuration = engineDirectory.resolve("engine.properties");
        if (!Files.isRegularFile(configuration, LinkOption.NOFOLLOW_LINKS)) {
            DesktopExtensionEngineInstaller.install(engineDirectory);
        }
        DesktopExtensionEngine started = open(dataDirectory, transport);
        if (!started.available()) {
            throw new IllegalStateException(started.diagnostic);
        }
        bridge = started.bridge;
        process = started.process;
        installedPackageNames.clear();
        installedPackageNames.addAll(started.installedPackageNames);
        diagnostic = started.diagnostic;
    }

    private synchronized void restartEngine() {
        stopProcess(process);
        process = null;
        Properties properties = load(engineDirectory.resolve("engine.properties"));
        Path approvedJar = approvedJar(engineDirectory, properties);
        String expectedSha256 = expectedSha256(properties);
        requireChecksum(approvedJar, expectedSha256);
        Path runtimeJar = prepareRuntimeCopy(engineDirectory, approvedJar, expectedSha256);
        DesktopExtensionEngine restarted = start(dataDirectory, engineDirectory, runtimeJar, transport);
        bridge = restarted.bridge;
        process = restarted.process;
        installedPackageNames.clear();
        installedPackageNames.addAll(restarted.installedPackageNames);
        diagnostic = restarted.diagnostic;
    }

    private synchronized int repairWhileStopped(Path convertedJar) {
        stopProcess(process);
        process = null;
        int repairs = 0;
        RuntimeException repairFailure = null;
        try {
            repairs = JvmExtensionBytecodeRepair.repair(
                    convertedJar,
                    engineDirectory.resolve("runtime").resolve("engine-runtime.jar"));
        } catch (RuntimeException exception) {
            repairFailure = exception;
        }
        try {
            restartEngine();
        } catch (RuntimeException restartFailure) {
            if (repairFailure != null) {
                restartFailure.addSuppressed(repairFailure);
            }
            throw restartFailure;
        }
        if (repairFailure != null) {
            throw repairFailure;
        }
        return repairs;
    }

    private Path convertedExtensionJar(URI artifact) {
        String path = artifact.getPath();
        String fileName = path == null ? "" : Path.of(path).getFileName().toString();
        if (!fileName.toLowerCase(Locale.ROOT).endsWith(".apk")) {
            throw new IllegalStateException("Installed APK URL does not expose a stable file name");
        }
        String jarName = fileName.substring(0, fileName.length() - 4) + ".jar";
        Path extensions = engineDirectory.resolve("data").resolve("extensions").toAbsolutePath().normalize();
        Path converted = extensions.resolve(jarName).normalize();
        if (!extensions.equals(converted.getParent())) {
            throw new IllegalStateException("Converted extension path escaped the engine directory");
        }
        return converted;
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
                DesktopApkEngineClient bridge = new MiwayomiDesktopApkEngineClient(new MiwayomiSourceBridge(
                        URI.create("http://127.0.0.1:" + port + "/"),
                        client));
                ProcessHandle processHandle = process.toHandle();
                awaitHealthy(bridge, processHandle);
                writeRuntimeState(engineDirectory, processHandle, port, runtimeJar);
                List<URI> repositories = new FileExtensionRepositoryStore(
                        dataDirectory.resolve("extension-repositories.txt")).load().stream()
                        .map(ExtensionRepositoryLocations::indexCandidates)
                        .map(List::getFirst)
                        .toList();
                bridge.saveRepositories(repositories);
                List<AnilibPlugin> sources = bridge.sourceBundles();
                Set<String> installedPackages = bridge.installedPackageNames();
                return new DesktopExtensionEngine(
                        dataDirectory,
                        engineDirectory,
                        transport,
                        bridge,
                        processHandle,
                        sources,
                        installedPackages,
                        "Desktop APK engine is running with " + sources.size() + " source bundles.");
            } catch (RuntimeException exception) {
                failure = exception;
                if (process != null && process.isAlive()) {
                    process.destroyForcibly();
                }
                if (process != null) {
                    clearRuntimeState(engineDirectory, process.toHandle());
                }
            }
        }
        throw new IllegalStateException("Extension engine did not become ready", failure);
    }

    private static DesktopExtensionEngine resume(
            Path dataDirectory,
            Path engineDirectory,
            HttpTransport transport) {
        Path stateFile = runtimeStateFile(engineDirectory);
        if (!Files.isRegularFile(stateFile, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(stateFile)) {
            return null;
        }
        try {
            Properties state = load(stateFile);
            long pid = Long.parseLong(requireProperty(state, "pid"));
            int port = Integer.parseInt(requireProperty(state, "port"));
            if (pid <= 0 || port < 1 || port > 65_535) {
                return null;
            }
            Path runtimeJar = engineDirectory.resolve("runtime").resolve("engine-runtime.jar")
                    .toAbsolutePath().normalize();
            Path engineData = engineDirectory.resolve("data").toAbsolutePath().normalize();
            if (!runtimeJar.toString().equals(requireProperty(state, "runtime"))
                    || !engineData.toString().equals(requireProperty(state, "data"))) {
                return null;
            }
            ProcessHandle handle = ProcessHandle.of(pid).filter(ProcessHandle::isAlive).orElse(null);
            if (handle == null || !matchesRuntimeCommand(handle, runtimeJar, engineData, port)) {
                return null;
            }
            AnilibHttpClient client = request -> transport.exchange(request, request.headers());
            DesktopApkEngineClient bridge = new MiwayomiDesktopApkEngineClient(new MiwayomiSourceBridge(
                    URI.create("http://127.0.0.1:" + port + "/"), client));
            bridge.requireHealthy();
            List<URI> repositories = new FileExtensionRepositoryStore(
                    dataDirectory.resolve("extension-repositories.txt")).load().stream()
                    .map(ExtensionRepositoryLocations::indexCandidates)
                    .map(List::getFirst)
                    .toList();
            bridge.saveRepositories(repositories);
            List<AnilibPlugin> sources = bridge.sourceBundles();
            Set<String> installedPackages = bridge.installedPackageNames();
            return new DesktopExtensionEngine(
                    dataDirectory,
                    engineDirectory,
                    transport,
                    bridge,
                    handle,
                    sources,
                    installedPackages,
                    "Desktop APK engine resumed with " + sources.size() + " source bundles.");
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private static boolean matchesRuntimeCommand(
            ProcessHandle process,
            Path runtimeJar,
            Path engineData,
            int port) {
        String command = process.info().commandLine().orElse("").toLowerCase(Locale.ROOT);
        return command.contains(runtimeJar.toString().toLowerCase(Locale.ROOT))
                && command.contains(engineData.toString().toLowerCase(Locale.ROOT))
                && command.contains("--port " + port)
                && command.contains("--host 127.0.0.1");
    }

    private static void writeRuntimeState(
            Path engineDirectory,
            ProcessHandle process,
            int port,
            Path runtimeJar) {
        Path stateFile = runtimeStateFile(engineDirectory);
        Path temporary = null;
        try {
            Files.createDirectories(stateFile.getParent());
            if (Files.exists(stateFile, LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(stateFile)) {
                throw new IllegalStateException("Extension engine runtime state must not be a symbolic link");
            }
            Properties state = new Properties();
            state.setProperty("pid", Long.toString(process.pid()));
            state.setProperty("port", Integer.toString(port));
            state.setProperty("runtime", runtimeJar.toAbsolutePath().normalize().toString());
            state.setProperty("data", engineDirectory.resolve("data").toAbsolutePath().normalize().toString());
            temporary = Files.createTempFile(stateFile.getParent(), ".process-", ".tmp");
            try (var output = Files.newOutputStream(temporary)) {
                state.store(output, "Anilib extension engine runtime");
            }
            try {
                Files.move(temporary, stateFile, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, stateFile, StandardCopyOption.REPLACE_EXISTING);
            }
            temporary = null;
        } catch (IOException exception) {
            throw new UncheckedIOException("Unable to persist extension engine runtime state", exception);
        } finally {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException ignored) {
                }
            }
        }
    }

    private static void clearRuntimeState(Path engineDirectory, ProcessHandle process) {
        if (process == null) {
            return;
        }
        Path stateFile = runtimeStateFile(engineDirectory);
        try {
            if (!Files.isRegularFile(stateFile, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(stateFile)) {
                return;
            }
            Properties state = load(stateFile);
            if (Long.toString(process.pid()).equals(state.getProperty("pid"))) {
                Files.deleteIfExists(stateFile);
            }
        } catch (RuntimeException | IOException ignored) {
        }
    }

    private static Path runtimeStateFile(Path engineDirectory) {
        return engineDirectory.resolve("runtime").resolve(RUNTIME_STATE_FILE);
    }

    private static Process launch(Path engineDirectory, Path runtimeJar, int port) {
        Path java = javaExecutable();
        Path engineData = engineDirectory.resolve("data");
        Path log = engineDirectory.resolve("engine.log");
        try {
            Files.createDirectories(engineData);
            Files.writeString(log, "", StandardCharsets.UTF_8);
            List<String> command = new java.util.ArrayList<>();
            command.add(java.toString());
            if (requiresFallbackTruffleRuntime()) {
                command.add("-Dtruffle.TruffleRuntime=com.oracle.truffle.api.impl.DefaultTruffleRuntime");
                command.add("-Dpolyglot.engine.WarnInterpreterOnly=false");
            }
            command.addAll(List.of(
                    "-jar", runtimeJar.toString(),
                    "--host", "127.0.0.1",
                    "--port", Integer.toString(port),
                    "--data", engineData.toString(),
                    "--no-open"));
            ProcessBuilder builder = new ProcessBuilder(command);
            builder.directory(engineDirectory.toFile());
            builder.redirectErrorStream(true);
            builder.redirectOutput(ProcessBuilder.Redirect.appendTo(log.toFile()));
            sanitizeEnvironment(builder);
            return builder.start();
        } catch (IOException exception) {
            throw new UncheckedIOException("Unable to launch desktop APK engine", exception);
        }
    }

    private static void awaitHealthy(DesktopApkEngineClient bridge, ProcessHandle process) {
        long deadline = System.nanoTime() + START_TIMEOUT.toNanos();
        RuntimeException failure = null;
        while (System.nanoTime() < deadline) {
            if (!process.isAlive()) {
                throw new IllegalStateException("Extension engine exited before becoming healthy", failure);
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
            MiwayomiRuntimePatcher.apply(runtimeJar);
            return runtimeJar;
        } catch (IOException exception) {
            throw new UncheckedIOException("Unable to prepare isolated engine copy", exception);
        }
    }

    private static void repairConvertedExtensions(Path engineDirectory, Path runtimeJar) {
        Path extensions = engineDirectory.resolve("data").resolve("extensions");
        if (!Files.isDirectory(extensions, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(extensions)) {
            return;
        }
        try (var files = Files.list(extensions)) {
            files.filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar"))
                    .filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                    .filter(path -> !Files.isSymbolicLink(path))
                    .forEach(path -> JvmExtensionBytecodeRepair.repair(path, runtimeJar));
        } catch (IOException exception) {
            throw new UncheckedIOException("Unable to inspect converted desktop extensions", exception);
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

    private static boolean requiresFallbackTruffleRuntime() {
        String operatingSystem = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String architecture = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        return operatingSystem.contains("win") && (architecture.equals("aarch64") || architecture.equals("arm64"));
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

    private static DesktopExtensionEngine unavailable(
            Path dataDirectory,
            HttpTransport transport,
            String diagnostic,
            Path engineDirectory) {
        writeInstructions(engineDirectory);
        return new DesktopExtensionEngine(
                dataDirectory,
                engineDirectory,
                transport,
                null,
                null,
                List.of(),
                Set.of(),
                diagnostic);
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
