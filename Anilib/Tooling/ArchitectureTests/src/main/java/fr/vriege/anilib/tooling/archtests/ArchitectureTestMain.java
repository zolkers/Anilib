package fr.vriege.anilib.tooling.archtests;

import fr.vriege.anilib.configuration.standard.StandardAnilib;
import fr.vriege.anilib.feature.library.LibraryCapabilities;
import fr.vriege.anilib.feature.discovery.DiscoveryCapabilities;
import fr.vriege.anilib.feature.library.LibraryCatalog;
import fr.vriege.anilib.feature.library.LibraryItem;
import fr.vriege.anilib.feature.library.MediaKind;
import fr.vriege.anilib.feature.localsource.LocalSourceCapabilities;
import fr.vriege.anilib.feature.network.NetworkCapabilities;
import fr.vriege.anilib.feature.reader.ReaderCapabilities;
import fr.vriege.anilib.feature.reader.ui.ReaderUiCapabilities;
import fr.vriege.anilib.feature.settings.SettingsCapabilities;
import fr.vriege.anilib.feature.settings.ui.SettingsUiCapabilities;
import fr.vriege.anilib.feature.player.PlayerCapabilities;
import fr.vriege.anilib.feature.player.ui.PlayerUiCapabilities;
import fr.vriege.anilib.feature.tracker.TrackerCapabilities;
import fr.vriege.anilib.feature.tracker.ui.TrackerUiCapabilities;
import fr.vriege.anilib.feature.updates.UpdateCapabilities;
import fr.vriege.anilib.feature.updates.ui.UpdateUiCapabilities;
import fr.vriege.anilib.feature.downloads.DownloadCapabilities;
import fr.vriege.anilib.feature.downloads.ui.DownloadUiCapabilities;
import fr.vriege.anilib.feature.extensionrepository.ExtensionRepositoryCapabilities;
import fr.vriege.anilib.feature.extensionrepository.ui.ExtensionRepositoryUiCapabilities;
import fr.vriege.anilib.feature.backup.BackupCapabilities;
import fr.vriege.anilib.feature.backup.ui.BackupUiCapabilities;
import fr.vriege.anilib.feature.library.ui.LibraryUiCapabilities;
import fr.vriege.anilib.feature.source.SourceCapabilities;
import fr.vriege.anilib.feature.source.SourceId;
import fr.vriege.anilib.feature.source.SourceRegistry;
import fr.vriege.anilib.foundation.component.ComponentDescriptor;
import fr.vriege.anilib.kernel.AnilibPlugin;
import fr.vriege.anilib.kernel.CapabilityKey;
import fr.vriege.anilib.kernel.Contribution;
import fr.vriege.anilib.kernel.ContributionPoint;
import fr.vriege.anilib.kernel.PluginManifest;
import fr.vriege.anilib.kernel.PluginStartupException;
import fr.vriege.anilib.kernel.StartedAnilib;
import fr.vriege.anilib.kernel.runtime.DefaultPluginEngine;

import java.util.ArrayList;
import java.util.List;
import java.nio.file.Path;
import java.nio.file.Files;
import java.io.IOException;

/** Dependency-free executable architecture contract suite. */
public final class ArchitectureTestMain {
    private static int assertions;

    private ArchitectureTestMain() {
    }

    public static void main(String[] arguments) {
        standardProductPublishesLibrary();
        graphRejectsMissingCapability();
        graphRejectsDuplicateProvider();
        graphRejectsCycles();
        graphRejectsMissingPublication();
        installationRollsBackInReverseOrder();
        contributionsAreOrderedDeterministically();
        assertions += LibraryPersistenceTest.run();
        assertions += LocalSourceTest.run();
        assertions += CoverCacheTest.run();
        assertions += LibraryPresentationTest.run();
        assertions += SourceExtensionSdkTest.run();
        assertions += SourceExtensionIsolationRuleTest.run();
        assertions += DesktopReleaseRuleTest.run();
        assertions += AndroidReleaseRuleTest.run();
        assertions += ExtensionRepositoryTest.run();
        assertions += PortableBundleLoadingTest.run();
        assertions += DiscoveryTest.run();
        assertions += HttpFrameworkTest.run();
        assertions += ReaderTest.run();
        assertions += PlayerTest.run();
        assertions += TrackerTest.run();
        assertions += UpdateTest.run();
        assertions += DownloadTest.run();
        assertions += BackupTest.run();
        assertions += SettingsTest.run();
        System.out.println("Architecture tests: " + assertions + " assertions passed.");
    }

    private static void standardProductPublishesLibrary() {
        Path dataDirectory;
        try {
            dataDirectory = Files.createTempDirectory("anilib-standard-test");
        } catch (IOException exception) {
            throw new AssertionError("Unable to create standard product test directory", exception);
        }
        try (StartedAnilib application = StandardAnilib.start(dataDirectory)) {
            LibraryCatalog catalog = application.capability(LibraryCapabilities.CATALOG);
            LibraryItem item = LibraryItem.create("A test title", MediaKind.MANGA);
            catalog.save(item);
            check(catalog.find(item.id()).orElseThrow().equals(item), "library must return saved item");
            check(application.components().size() == 13, "standard product must install thirteen bootstrap bundles");
            check(application.capability(ExtensionRepositoryCapabilities.SERVICE).repositories().isEmpty(),
                    "standard product must ship without a third-party extension repository");
            check(application.capability(ExtensionRepositoryCapabilities.INSTALLATION).installed().isEmpty(),
                    "standard product must start without installed third-party extensions");
            check(application.capability(ExtensionRepositoryUiCapabilities.PRESENTATION) != null,
                    "extension repository Bundle must publish its shared presentation");
            check(application.capability(LocalSourceCapabilities.CONTENT).publications().isEmpty(),
                    "standard product must expose the local source capability");
            SourceRegistry sourceRegistry = application.capability(SourceCapabilities.REGISTRY);
            check(sourceRegistry.sources().size() == 1,
                    "standard product must register its local source explicitly");
            check(sourceRegistry.find(SourceId.of("anilib.local")).isPresent(),
                    "standard product must expose the local source by stable id");
            check(application.capability(NetworkCapabilities.HTTP_CLIENT) != null,
                    "standard product must publish the HTTP client capability");
            check(application.capability(NetworkCapabilities.MAINTENANCE) != null,
                    "standard product must publish user-facing network maintenance");
            check(application.capability(SettingsCapabilities.SERVICE) != null,
                    "Settings Bundle must publish durable application preferences");
            check(application.capability(SettingsUiCapabilities.PRESENTATION) != null,
                    "Settings Bundle must publish its shared presentation");
            check(application.capability(LibraryUiCapabilities.PRESENTATION).library().titles().size() == 1,
                    "Library Bundle must publish its presentation capability");
            check(application.capability(LibraryCapabilities.BACKUP_CODEC) != null,
                    "Library Bundle must publish its self-owned backup codec");
            check(application.capability(DiscoveryCapabilities.BACKUP_CODEC) != null,
                    "Discovery Bundle must publish its self-owned backup codec");
            check(application.capability(ReaderCapabilities.SERVICE) != null,
                    "Reader Bundle must publish its reader capability");
            check(application.capability(ReaderUiCapabilities.PRESENTATION) != null,
                    "Reader Bundle must publish its shared presentation capability");
            check(application.capability(PlayerCapabilities.SERVICE) != null,
                    "Player Bundle must publish its episode capability");
            check(!application.capability(PlayerCapabilities.BACKEND).available(),
                    "headless Standard product must publish its explicit unavailable media backend");
            check(application.capability(PlayerCapabilities.BACKUP_CODEC) != null,
                    "Player Bundle must publish its self-owned backup codec");
            check(application.capability(PlayerUiCapabilities.PRESENTATION) != null,
                    "Player Bundle must publish its shared presentation capability");
            check(application.capability(TrackerCapabilities.REGISTRY).trackers().isEmpty(),
                    "Tracker Bundle must install without implicitly selecting a remote tracker");
            check(application.capability(TrackerCapabilities.SERVICE) != null,
                    "Tracker Bundle must publish its orchestration capability");
            check(application.capability(TrackerCapabilities.BACKUP_CODEC) != null,
                    "Tracker Bundle must publish its self-owned backup codec");
            check(application.capability(TrackerUiCapabilities.PRESENTATION) != null,
                    "Tracker Bundle must publish its shared presentation capability");
            check(application.capability(UpdateCapabilities.SERVICE) != null,
                    "Updates Bundle must publish its background update capability");
            check(application.capability(UpdateCapabilities.BACKUP_CODEC) != null,
                    "Updates Bundle must publish its self-owned backup codec");
            check(application.capability(UpdateUiCapabilities.PRESENTATION) != null,
                    "Updates Bundle must publish its shared presentation capability");
            check(application.capability(DownloadCapabilities.SERVICE) != null,
                    "Downloads Bundle must publish its queue capability");
            check(application.capability(DownloadUiCapabilities.PRESENTATION) != null,
                    "Downloads Bundle must publish its shared presentation capability");
            check(application.capability(BackupCapabilities.SERVICE) != null,
                    "Backup Bundle must publish its archive capability");
            check(application.capability(BackupUiCapabilities.PRESENTATION) != null,
                    "Backup Bundle must publish its shared presentation capability");
            check(catalog.remove(item.id()), "library must remove existing item");
        } finally {
            deleteDirectory(dataDirectory);
        }
    }

    private static void deleteDirectory(Path directory) {
        try (java.util.stream.Stream<Path> entries = Files.walk(directory)) {
            for (Path entry : entries.sorted(java.util.Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(entry);
            }
        } catch (IOException exception) {
            throw new AssertionError("Unable to clean test directory " + directory, exception);
        }
    }

    private static void graphRejectsMissingCapability() {
        CapabilityKey<String> missing = CapabilityKey.of("test.missing", String.class);
        AnilibPlugin consumer = plugin("test.consumer", builder("test.consumer").requires(missing).build(),
                context -> context.require(missing));
        expectStartupFailure(() -> new DefaultPluginEngine().start(List.of(consumer)), "missing capability");
    }

    private static void graphRejectsDuplicateProvider() {
        CapabilityKey<String> shared = CapabilityKey.of("test.shared", String.class);
        AnilibPlugin first = provider("test.first", shared, "first");
        AnilibPlugin second = provider("test.second", shared, "second");
        expectStartupFailure(() -> new DefaultPluginEngine().start(List.of(first, second)), "duplicate provider");
    }

    private static void graphRejectsCycles() {
        CapabilityKey<String> firstKey = CapabilityKey.of("test.cycle.first", String.class);
        CapabilityKey<String> secondKey = CapabilityKey.of("test.cycle.second", String.class);
        AnilibPlugin first = plugin(
                "test.cycle-first",
                builder("test.cycle-first").requires(secondKey).provides(firstKey).build(),
                context -> context.publish(firstKey, context.require(secondKey)));
        AnilibPlugin second = plugin(
                "test.cycle-second",
                builder("test.cycle-second").requires(firstKey).provides(secondKey).build(),
                context -> context.publish(secondKey, context.require(firstKey)));
        expectStartupFailure(() -> new DefaultPluginEngine().start(List.of(first, second)), "cycle");
    }

    private static void graphRejectsMissingPublication() {
        CapabilityKey<String> promised = CapabilityKey.of("test.promised", String.class);
        AnilibPlugin plugin = plugin(
                "test.empty-provider",
                builder("test.empty-provider").provides(promised).build(),
                context -> { });
        expectStartupFailure(() -> new DefaultPluginEngine().start(List.of(plugin)), "missing publication");
    }

    private static void installationRollsBackInReverseOrder() {
        CapabilityKey<String> ready = CapabilityKey.of("test.ready", String.class);
        List<String> closed = new ArrayList<>();
        AnilibPlugin first = plugin(
                "test.rollback-first",
                builder("test.rollback-first").provides(ready).build(),
                context -> {
                    context.onClose(() -> closed.add("first"));
                    context.publish(ready, "ready");
                });
        AnilibPlugin second = plugin(
                "test.rollback-second",
                builder("test.rollback-second").requires(ready).build(),
                context -> {
                    context.require(ready);
                    context.onClose(() -> closed.add("second"));
                    throw new IllegalStateException("deliberate failure");
                });

        expectStartupFailure(() -> new DefaultPluginEngine().start(List.of(first, second)), "rollback");
        check(closed.equals(List.of("second", "first")), "rollback must close resources in reverse order");
    }

    private static void contributionsAreOrderedDeterministically() {
        ContributionPoint<String> pages = ContributionPoint.of("test.pages", String.class);
        AnilibPlugin lower = contributor("test.lower", pages, 10, "lower");
        AnilibPlugin higher = contributor("test.higher", pages, 20, "higher");
        try (StartedAnilib application = new DefaultPluginEngine().start(List.of(lower, higher))) {
            List<String> values = application.contributions(pages).stream().map(Contribution::value).toList();
            check(values.equals(List.of("higher", "lower")), "contributions must use descending priority");
        }
    }

    private static AnilibPlugin provider(String id, CapabilityKey<String> key, String value) {
        return plugin(id, builder(id).provides(key).build(), context -> context.publish(key, value));
    }

    private static AnilibPlugin contributor(
            String id,
            ContributionPoint<String> point,
            int priority,
            String value) {
        return plugin(id, builder(id).contributesTo(point).build(),
                context -> context.contribute(point, priority, value));
    }

    private static PluginManifest.Builder builder(String id) {
        return PluginManifest.builder(ComponentDescriptor.of(id, id, "test"));
    }

    private static AnilibPlugin plugin(String id, PluginManifest manifest, Installer installer) {
        return new AnilibPlugin() {
            @Override
            public PluginManifest manifest() {
                return manifest;
            }

            @Override
            public void install(fr.vriege.anilib.kernel.PluginInstallationContext context) throws Exception {
                installer.install(context);
            }

            @Override
            public String toString() {
                return id;
            }
        };
    }

    private static void expectStartupFailure(Runnable action, String scenario) {
        try {
            action.run();
            throw new AssertionError("Expected startup failure for " + scenario);
        } catch (PluginStartupException expected) {
            assertions++;
        }
    }

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    @FunctionalInterface
    private interface Installer {
        void install(fr.vriege.anilib.kernel.PluginInstallationContext context) throws Exception;
    }
}
