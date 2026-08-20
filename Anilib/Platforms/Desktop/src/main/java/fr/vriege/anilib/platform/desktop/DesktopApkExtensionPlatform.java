package fr.vriege.anilib.platform.desktop;

import fr.vriege.anilib.feature.extensionrepository.ExtensionArtifactFormat;
import fr.vriege.anilib.feature.extensionrepository.ExtensionPackageMetadata;
import fr.vriege.anilib.feature.extensionrepository.runtime.DesktopExtensionSourceBridge;
import fr.vriege.anilib.feature.extensionrepository.runtime.ExtensionRepositoryLocations;
import fr.vriege.anilib.feature.extensionrepository.runtime.FileExtensionRepositoryStore;
import fr.vriege.anilib.feature.extensionrepository.ui.ApkExtensionPlatform;
import fr.vriege.anilib.foundation.component.ComponentId;
import fr.vriege.anilib.framework.http.AnilibHttpClient;
import fr.vriege.anilib.framework.http.HttpTransport;
import fr.vriege.anilib.kernel.AnilibPlugin;
import fr.vriege.anilib.kernel.PluginRegistration;
import fr.vriege.anilib.kernel.StartedAnilib;
import fr.vriege.anilib.platform.desktopextensionhost.EmbeddedDesktopExtensionHost;

import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

final class DesktopApkExtensionPlatform implements ApkExtensionPlatform, AutoCloseable {
    private final Path dataDirectory;
    private final HttpTransport transport;
    private volatile EmbeddedDesktopExtensionHost host;
    private volatile DesktopExtensionSourceBridge bridge;
    private final List<AnilibPlugin> sourceBundles;
    private final Map<String, List<PluginRegistration>> dynamicRegistrations = new LinkedHashMap<>();
    private final Set<String> installedPackageNames = new LinkedHashSet<>();
    private volatile StartedAnilib started;
    private volatile String diagnostic;

    private DesktopApkExtensionPlatform(
            Path dataDirectory,
            HttpTransport transport,
            EmbeddedDesktopExtensionHost host,
            DesktopExtensionSourceBridge bridge,
            List<AnilibPlugin> sourceBundles,
            Set<String> installedPackages,
            String diagnostic) {
        this.dataDirectory = dataDirectory;
        this.transport = transport;
        this.host = host;
        this.bridge = bridge;
        this.sourceBundles = List.copyOf(sourceBundles);
        this.installedPackageNames.addAll(installedPackages);
        this.diagnostic = diagnostic;
    }

    static DesktopApkExtensionPlatform open(Path dataDirectory, HttpTransport transport) {
        Path data = Objects.requireNonNull(dataDirectory, "dataDirectory").toAbsolutePath().normalize();
        try {
            return running(data, transport);
        } catch (RuntimeException | LinkageError exception) {
            return new DesktopApkExtensionPlatform(
                    data,
                    transport,
                    null,
                    null,
                    List.of(),
                    Set.of(),
                    "Anilib's desktop extension host could not start: " + message(exception));
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
        return host != null && bridge != null;
    }

    @Override
    public boolean installationSupported() {
        return true;
    }

    @Override
    public String availabilityDescription() {
        return available()
                ? "Anilib's desktop extension host runs installed manga and anime APK sources immediately."
                : diagnostic;
    }

    @Override
    public String installActionLabel() {
        return "Install for desktop";
    }

    @Override
    public String installProgressLabel() {
        return "Installing APK in Anilib's desktop extension host";
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
            String compatibilityFailure = bridge.compatibilityFailures().get(extensionPackage.packageName());
            if (compatibilityFailure != null) {
                throw new IllegalStateException(extensionPackage.displayName()
                        + " is installed but incompatible with the desktop host: " + compatibilityFailure);
            }
            if (activated == 0) {
                throw new IllegalStateException(extensionPackage.displayName()
                        + " was installed, but it did not expose a compatible source.");
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
            String result = bridge.uninstall(packageName);
            deactivate(packageName);
            return result + " Its sources were removed from Browse.";
        });
    }

    private synchronized int activateNewSources() {
        StartedAnilib product = started;
        if (product == null) {
            throw new IllegalStateException("Anilib product is not attached to the desktop extension host");
        }
        Set<ComponentId> active = product.components().stream()
                .map(component -> component.id())
                .collect(Collectors.toCollection(LinkedHashSet::new));
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
        if (registrations == null) {
            return;
        }
        registrations.reversed().forEach(registration -> {
            try {
                registration.close();
            } catch (RuntimeException ignored) {
            }
        });
    }

    private synchronized void ensureAvailable() {
        if (available()) {
            return;
        }
        DesktopApkExtensionPlatform restarted = running(dataDirectory, transport);
        host = restarted.host;
        bridge = restarted.bridge;
        installedPackageNames.clear();
        installedPackageNames.addAll(restarted.installedPackageNames);
        diagnostic = restarted.diagnostic;
    }

    private static DesktopApkExtensionPlatform running(Path dataDirectory, HttpTransport transport) {
        EmbeddedDesktopExtensionHost host = null;
        try {
            host = EmbeddedDesktopExtensionHost.start(dataDirectory.resolve("extension-engine").resolve("data"));
            AnilibHttpClient client = request -> transport.exchange(request, request.headers());
            DesktopExtensionSourceBridge bridge = new DesktopExtensionSourceBridge(
                    URI.create("http://127.0.0.1:" + host.port() + "/"), client);
            bridge.requireHealthy();
            List<URI> repositories = new FileExtensionRepositoryStore(
                    dataDirectory.resolve("extension-repositories.txt")).load().stream()
                    .map(ExtensionRepositoryLocations::indexCandidates)
                    .map(List::getFirst)
                    .toList();
            bridge.saveRepositories(repositories);
            List<AnilibPlugin> sources = bridge.sourceBundles();
            Set<String> installedPackages = bridge.installedPackageNames();
            return new DesktopApkExtensionPlatform(
                    dataDirectory,
                    transport,
                    host,
                    bridge,
                    sources,
                    installedPackages,
                    "Anilib desktop extension host is running with " + sources.size() + " source bundles.");
        } catch (RuntimeException | LinkageError exception) {
            if (host != null) {
                host.close();
            }
            throw exception;
        }
    }

    private static String message(Throwable exception) {
        return exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
    }

    @Override
    public synchronized void close() {
        EmbeddedDesktopExtensionHost running = host;
        host = null;
        bridge = null;
        if (running != null) {
            running.close();
        }
    }
}
