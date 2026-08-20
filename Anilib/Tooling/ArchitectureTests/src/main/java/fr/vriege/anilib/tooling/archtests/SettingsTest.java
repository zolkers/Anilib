package fr.vriege.anilib.tooling.archtests;

import fr.vriege.anilib.feature.settings.SettingsSnapshot;
import fr.vriege.anilib.feature.settings.DiagnosticResetArea;
import fr.vriege.anilib.feature.settings.BrowserPolicy;
import fr.vriege.anilib.feature.settings.AccentColor;
import fr.vriege.anilib.feature.settings.LanguagePack;
import fr.vriege.anilib.feature.settings.NavigationStyle;
import fr.vriege.anilib.feature.settings.PlayerWindowMode;
import fr.vriege.anilib.feature.settings.StartScreen;
import fr.vriege.anilib.feature.settings.ThemeFamily;
import fr.vriege.anilib.feature.settings.ThemeMode;
import fr.vriege.anilib.feature.settings.TypographyScale;
import fr.vriege.anilib.feature.settings.runtime.FileSettingsService;
import fr.vriege.anilib.feature.settings.runtime.FileDiagnosticService;
import fr.vriege.anilib.feature.settings.runtime.DefaultUnusedDataMaintenance;
import fr.vriege.anilib.feature.settings.ui.DefaultSettingsPresentation;
import fr.vriege.anilib.feature.settings.ui.SettingsPresentation;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.Set;
import fr.vriege.anilib.feature.settings.UnusedDataCleanupResult;
import java.util.Map;
import java.util.stream.Stream;

final class SettingsTest {
    private SettingsTest() {
    }

    static int run() {
        Counter counter = new Counter();
        verifiesUnusedDataCoordination(counter);
        Path directory = temporaryDirectory();
        Path file = directory.resolve("settings.properties");
        try {
            FileSettingsService service = new FileSettingsService(file);
            FileDiagnosticService diagnostics = new FileDiagnosticService(directory);
            counter.check(service.snapshot().equals(SettingsSnapshot.defaults()),
                    "settings must expose safe defaults before the first write");

            SettingsPresentation presentation = new DefaultSettingsPresentation(
                    service,
                    UnusedDataCleanupResult::empty,
                    diagnostics);
            AtomicInteger observations = new AtomicInteger();
            AutoCloseable observation = presentation.observe(ignored -> observations.incrementAndGet());
            counter.check(observations.get() == 1,
                    "settings observers must receive the current snapshot immediately");

            presentation.setLanguagePack(LanguagePack.FRENCH);
            presentation.setThemeMode(ThemeMode.DARK);
            presentation.setThemeFamily(ThemeFamily.AMOLED);
            presentation.setAccentColor(AccentColor.SAKURA);
            presentation.setTypographyScale(TypographyScale.LARGE);
            presentation.setReducedMotion(true);
            presentation.setNavigationStyle(NavigationStyle.NAVIGATION_RAIL);
            presentation.setPlayerWindowMode(PlayerWindowMode.FULLSCREEN);
            BrowserPolicy browserPolicy = new BrowserPolicy(
                    true, true, false, false, true, true, 125);
            presentation.setBrowserPolicy(browserPolicy);
            presentation.setStartScreen(StartScreen.BROWSE);
            presentation.setShowAdultContent(true);
            presentation.setIncognitoMode(true);
            presentation.setDownloadOnlyOnWifi(false);
            presentation.setUpdateOnlyOnWifi(false);
            counter.check(observations.get() == 15,
                    "every settings change must publish one immutable snapshot");
            close(observation);

            SettingsSnapshot expected = new SettingsSnapshot(
                    LanguagePack.FRENCH,
                    ThemeMode.DARK,
                    ThemeFamily.AMOLED,
                    AccentColor.SAKURA,
                    TypographyScale.LARGE,
                    true,
                    NavigationStyle.NAVIGATION_RAIL,
                    PlayerWindowMode.FULLSCREEN,
                    browserPolicy,
                    StartScreen.BROWSE,
                    true,
                    true,
                    false,
                    false);
            counter.check(service.snapshot().equals(expected),
                    "settings actions must update the in-memory snapshot");
            counter.check(new FileSettingsService(file).snapshot().equals(expected),
                    "settings must survive a service restart");

            presentation.setThemeMode(ThemeMode.LIGHT);
            counter.check(observations.get() == 15,
                    "closed settings observations must stop receiving changes");
            diagnostics.recordLog("settings test log");
            diagnostics.recordCrash("settings test crash", "bounded details");
            counter.check(presentation.diagnostics().reports().size() == 2,
                    "diagnostics must expose recorded logs and crash reports");
            counter.check(Files.isRegularFile(presentation.exportDiagnostics()),
                    "diagnostics must export a bounded archive");
            var reset = presentation.planReset(Set.of(
                    DiagnosticResetArea.LOGS,
                    DiagnosticResetArea.CRASH_REPORTS));
            presentation.executeReset(reset);
            counter.check(presentation.diagnostics().reports().isEmpty(),
                    "confirmed diagnostic reset must remove only selected reports");
            reset = presentation.planReset(Set.of(DiagnosticResetArea.SETTINGS));
            presentation.executeReset(reset);
            counter.check(presentation.snapshot().equals(SettingsSnapshot.defaults()),
                    "settings reset must restore and persist safe defaults");
            return counter.value;
        } finally {
            deleteDirectory(directory);
        }
    }

    private static void verifiesUnusedDataCoordination(Counter counter) {
        DefaultUnusedDataMaintenance maintenance = new DefaultUnusedDataMaintenance();
        AutoCloseable first = maintenance.register("player", () -> 2);
        maintenance.register("downloads", () -> 1);
        counter.check(maintenance.clean().totalRemoved() == 3,
                "unused-data maintenance must aggregate every registered feature cleaner");
        close(first);
        counter.check(maintenance.clean().removedByOwner().equals(Map.of("downloads", 1)),
                "closing a feature registration must remove its unused-data cleaner");
    }

    private static Path temporaryDirectory() {
        try {
            return Files.createTempDirectory("anilib-settings-test");
        } catch (IOException exception) {
            throw new AssertionError("Unable to create settings test directory", exception);
        }
    }

    private static void close(AutoCloseable closeable) {
        try {
            closeable.close();
        } catch (Exception exception) {
            throw new AssertionError("Unable to close settings observation", exception);
        }
    }

    private static void deleteDirectory(Path directory) {
        try (Stream<Path> entries = Files.walk(directory)) {
            for (Path entry : entries.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(entry);
            }
        } catch (IOException exception) {
            throw new AssertionError("Unable to clean settings test directory", exception);
        }
    }

    private static final class Counter {
        private int value;

        private void check(boolean condition, String message) {
            value++;
            if (!condition) {
                throw new AssertionError(message);
            }
        }
    }
}
