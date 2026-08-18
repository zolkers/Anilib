package fr.vriege.anilib.tooling.archtests;

import fr.vriege.anilib.feature.settings.SettingsSnapshot;
import fr.vriege.anilib.feature.settings.AccentColor;
import fr.vriege.anilib.feature.settings.LanguagePack;
import fr.vriege.anilib.feature.settings.NavigationStyle;
import fr.vriege.anilib.feature.settings.StartScreen;
import fr.vriege.anilib.feature.settings.ThemeFamily;
import fr.vriege.anilib.feature.settings.ThemeMode;
import fr.vriege.anilib.feature.settings.TypographyScale;
import fr.vriege.anilib.feature.settings.runtime.FileSettingsService;
import fr.vriege.anilib.feature.settings.runtime.DefaultUnusedDataMaintenance;
import fr.vriege.anilib.feature.settings.ui.DefaultSettingsPresentation;
import fr.vriege.anilib.feature.settings.ui.SettingsPresentation;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.concurrent.atomic.AtomicInteger;

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
            counter.check(service.snapshot().equals(SettingsSnapshot.defaults()),
                    "settings must expose safe defaults before the first write");

            SettingsPresentation presentation = new DefaultSettingsPresentation(service);
            AtomicInteger observations = new AtomicInteger();
            AutoCloseable observation = presentation.observe(ignored -> observations.incrementAndGet());
            counter.check(observations.get() == 1,
                    "settings observers must receive the current snapshot immediately");

            presentation.setLanguagePack(LanguagePack.FRENCH);
            presentation.setThemeMode(ThemeMode.DARK);
            presentation.setThemeFamily(ThemeFamily.AMOLED);
            presentation.setAccentColor(AccentColor.SAKURA);
            presentation.setTypographyScale(TypographyScale.LARGE);
            presentation.setNavigationStyle(NavigationStyle.NAVIGATION_RAIL);
            presentation.setStartScreen(StartScreen.BROWSE);
            presentation.setShowAdultContent(true);
            presentation.setIncognitoMode(true);
            presentation.setDownloadOnlyOnWifi(false);
            presentation.setUpdateOnlyOnWifi(false);
            counter.check(observations.get() == 12,
                    "every settings change must publish one immutable snapshot");
            close(observation);

            SettingsSnapshot expected = new SettingsSnapshot(
                    LanguagePack.FRENCH,
                    ThemeMode.DARK,
                    ThemeFamily.AMOLED,
                    AccentColor.SAKURA,
                    TypographyScale.LARGE,
                    NavigationStyle.NAVIGATION_RAIL,
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
            counter.check(observations.get() == 12,
                    "closed settings observations must stop receiving changes");
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
        counter.check(maintenance.clean().removedByOwner().equals(java.util.Map.of("downloads", 1)),
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
        try (java.util.stream.Stream<Path> entries = Files.walk(directory)) {
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
