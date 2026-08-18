package fr.vriege.anilib.feature.extensionrepository.runtime;

import fr.vriege.anilib.feature.extensionrepository.ExtensionArtifactFormat;
import fr.vriege.anilib.feature.extensionrepository.ExtensionBundleLoadFailure;
import fr.vriege.anilib.feature.extensionrepository.ExtensionInstallationState;
import fr.vriege.anilib.feature.extensionrepository.InstalledExtensionPackage;
import fr.vriege.anilib.feature.source.SourceExtensionFactory;
import fr.vriege.anilib.feature.source.SourceExtensionManifest;
import fr.vriege.anilib.feature.source.SourceExtensionPlugin;
import fr.vriege.anilib.feature.source.SourceSdk;
import fr.vriege.anilib.foundation.component.ComponentDescriptor;
import fr.vriege.anilib.foundation.validation.Preconditions;
import fr.vriege.anilib.kernel.AnilibPlugin;

import java.io.IOException;
import java.io.InputStream;
import java.lang.module.Configuration;
import java.lang.module.ModuleDescriptor;
import java.lang.module.ModuleFinder;
import java.lang.module.ModuleReference;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;

/** Loads enabled, verified source Bundles into one child module layer per artifact. */
public final class PortableSourceBundleLoader {
    private static final long MAX_ARTIFACT_BYTES = 16L * 1_024L * 1_024L;
    private static final Set<String> ALLOWED_REQUIRED_MODULES = Set.of(
            "java.base",
            "fr.vriege.anilib.feature.source.api");
    private static final HexFormat HEX = HexFormat.of();

    private final Path installationDirectory;

    public PortableSourceBundleLoader(Path installationDirectory) {
        this.installationDirectory = Preconditions.requireNonNull(
                        installationDirectory,
                        "installationDirectory")
                .toAbsolutePath()
                .normalize();
    }

    public PortableSourceBundleLoadResult load() {
        List<InstalledExtensionPackage> installed;
        try {
            installed = new FileInstalledExtensionStore(installationDirectory.resolve("installed.tsv"))
                    .load()
                    .values()
                    .stream()
                    .sorted(Comparator.comparing(InstalledExtensionPackage::packageName))
                    .toList();
        } catch (RuntimeException error) {
            return new PortableSourceBundleLoadResult(
                    List.of(),
                    List.of(failure("installed.extensions", error)));
        }

        List<AnilibPlugin> bundles = new ArrayList<>();
        List<ExtensionBundleLoadFailure> failures = new ArrayList<>();
        for (InstalledExtensionPackage extension : installed) {
            if (extension.format() != ExtensionArtifactFormat.ANILIB_BUNDLE
                    || extension.state() != ExtensionInstallationState.ENABLED) {
                continue;
            }
            try {
                bundles.addAll(load(extension));
            } catch (Exception | LinkageError error) {
                failures.add(failure(extension.packageName(), error));
            }
        }
        return new PortableSourceBundleLoadResult(bundles, failures);
    }

    private List<AnilibPlugin> load(InstalledExtensionPackage extension) throws IOException {
        Path artifact = PortableExtensionArtifacts.path(installationDirectory, extension);
        byte[] bytes = readArtifact(artifact);
        if (!MessageDigest.isEqual(
                extension.sha256().getBytes(java.nio.charset.StandardCharsets.US_ASCII),
                checksum(bytes).getBytes(java.nio.charset.StandardCharsets.US_ASCII))) {
            throw new SecurityException("Installed portable Bundle checksum no longer matches");
        }
        PortableSourceBundleDescriptor descriptor = PortableSourceBundleDescriptor.read(bytes);
        verifyInstalledMetadata(extension, descriptor);
        ModuleLayer layer = isolatedLayer(bytes, descriptor);
        Module module = layer.findModule(descriptor.moduleName()).orElseThrow();

        List<AnilibPlugin> plugins = new ArrayList<>();
        for (PortableSourceBundleDescriptor.SourceEntry source : descriptor.sources()) {
            SourceExtensionFactory factory = factory(module, source.factoryClass());
            ComponentDescriptor component = ComponentDescriptor.of(
                    source.componentId(),
                    source.displayName(),
                    extension.versionName());
            SourceExtensionManifest manifest = source.origins().isEmpty()
                    ? SourceExtensionManifest.offline(component, source.sourceId())
                    : SourceExtensionManifest.networked(component, source.sourceId(), source.origins());
            plugins.add(new SourceExtensionPlugin(manifest, factory));
        }
        return List.copyOf(plugins);
    }

    private static byte[] readArtifact(Path artifact) throws IOException {
        if (!Files.isRegularFile(artifact)) {
            throw new SecurityException("Installed portable Bundle artifact is missing");
        }
        try (InputStream input = Files.newInputStream(artifact)) {
            byte[] bytes = input.readNBytes((int) MAX_ARTIFACT_BYTES + 1);
            if (bytes.length > MAX_ARTIFACT_BYTES) {
                throw new SecurityException("Installed portable Bundle artifact is too large");
            }
            return bytes;
        }
    }

    private static ModuleLayer isolatedLayer(
            byte[] artifact,
            PortableSourceBundleDescriptor descriptor) {
        ModuleFinder finder = InMemoryModuleArchive.finder(artifact);
        Set<ModuleReference> modules = finder.findAll();
        if (modules.size() != 1) {
            throw new SecurityException("Portable Bundle must contain exactly one Java module");
        }
        ModuleDescriptor module = modules.iterator().next().descriptor();
        if (module.isAutomatic() || module.isOpen() || !module.name().equals(descriptor.moduleName())) {
            throw new SecurityException("Portable Bundle must be one matching explicit, closed Java module");
        }
        if (!module.uses().isEmpty() || !module.provides().isEmpty()) {
            throw new SecurityException("Portable Bundle cannot declare service discovery");
        }
        for (ModuleDescriptor.Requires requirement : module.requires()) {
            if (!ALLOWED_REQUIRED_MODULES.contains(requirement.name())) {
                throw new SecurityException(
                        "Portable Bundle requires forbidden module " + requirement.name());
            }
        }
        Configuration configuration = ModuleLayer.boot()
                .configuration()
                .resolve(finder, ModuleFinder.of(), Set.of(module.name()));
        return ModuleLayer.boot().defineModulesWithOneLoader(
                configuration,
                SourceExtensionFactory.class.getClassLoader());
    }

    private static SourceExtensionFactory factory(Module module, String className) {
        Class<?> type = Class.forName(module, className);
        if (type == null || !SourceExtensionFactory.class.isAssignableFrom(type)) {
            throw new SecurityException("Portable Bundle factory does not implement SourceExtensionFactory");
        }
        String packageName = type.getPackageName();
        if (!module.isExported(packageName, PortableSourceBundleLoader.class.getModule())) {
            throw new SecurityException("Portable Bundle factory package is not exported to Anilib");
        }
        try {
            Constructor<?> constructor = type.getConstructor();
            return SourceExtensionFactory.class.cast(constructor.newInstance());
        } catch (NoSuchMethodException | InstantiationException | IllegalAccessException exception) {
            throw new SecurityException(
                    "Portable Bundle factory needs an accessible public no-arg constructor",
                    exception);
        } catch (InvocationTargetException exception) {
            throw new SecurityException("Portable Bundle factory constructor failed", exception.getCause());
        }
    }

    private static void verifyInstalledMetadata(
            InstalledExtensionPackage extension,
            PortableSourceBundleDescriptor descriptor) {
        if (!extension.packageName().equals(descriptor.packageName())
                || extension.versionCode() != descriptor.versionCode()) {
            throw new SecurityException("Installed portable Bundle descriptor no longer matches its metadata");
        }
        if (!SourceSdk.API_VERSION.supports(descriptor.apiVersion())) {
            throw new SecurityException(
                    "Installed portable Bundle requires unsupported Source API " + descriptor.apiVersion());
        }
    }

    private static String checksum(byte[] bytes) {
        try {
            return HEX.formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JDK does not provide SHA-256", exception);
        }
    }

    private static ExtensionBundleLoadFailure failure(String packageName, Throwable error) {
        String message = error.getMessage();
        if (message == null || message.isBlank()) {
            message = error.getClass().getSimpleName();
        }
        return new ExtensionBundleLoadFailure(packageName, message);
    }
}
