package fr.vriege.anilib.tooling.archtests;

import fr.vriege.anilib.tooling.javaquality.AndroidReleaseRule;
import fr.vriege.anilib.tooling.javaquality.RepositorySnapshot;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

final class AndroidReleaseRuleTest {
    private AndroidReleaseRuleTest() {
    }

    static int run() {
        Path repository = temporaryDirectory();
        try {
            Path build = repository.resolve("Anilib/Platforms/Android/build.gradle");
            write(build, """
                    compileSdk = 37 minSdk = 26 targetSdk = 37
                    anilibAndroidVersionName anilibAndroidVersionCode
                    ANILIB_ANDROID_KEYSTORE ANILIB_ANDROID_KEYSTORE_PASSWORD
                    ANILIB_ANDROID_KEY_ALIAS ANILIB_ANDROID_KEY_PASSWORD
                    writeAndroidReleaseChecksums MessageDigest.getInstance('SHA-256')
                    """);
            write(repository.resolve("Anilib/Platforms/Android/module.properties"),
                    "layer=PLATFORM\nlanguage=KOTLIN\nexternalDependencies="
                            + "androidx.activity:activity-compose,androidx.preference:preference-ktx,"
                            + "io.github.kevinnzou:compose-webview-multiplatform\n");
            Path manifest = repository.resolve("Anilib/Platforms/Android/src/main/AndroidManifest.xml");
            write(manifest, """
                    android:usesCleartextTraffic="false" android:exported="false"
                    """);
            Path preflight = repository.resolve(
                    "Anilib/Platforms/Android/src/main/kotlin/fr/vriege/anilib/platform/android/"
                            + "AndroidAniyomiRuntimePreflight.kt");
            write(preflight, """
                    extension.signingCertificateSha256()::contains
                    Class.forName(className, false, applicationContext.classLoader)
                    ApkExtensionRuntimeState.HOST_ABI_MISSING
                    ANIME_HOST_CLASSES MANGA_HOST_CLASSES
                    """);
            Path inventory = repository.resolve(
                    "Anilib/Platforms/Android/src/main/kotlin/fr/vriege/anilib/platform/android/"
                            + "AndroidAniyomiExtensionInventory.kt");
            write(inventory, """
                    tachiyomi.animeextension tachiyomi.extension
                    ExtensionContentKind.ANIME ExtensionContentKind.MANGA
                    """);
            Path sourceRuntime = repository.resolve(
                    "Anilib/Platforms/Android/src/main/kotlin/fr/vriege/anilib/platform/android/"
                            + "AndroidAniyomiSourceRuntime.kt");
            write(sourceRuntime, """
                    PathClassLoader preflight.report(extension)
                    ApkExtensionRuntimeState.HOST_ABI_AVAILABLE
                    AniyomiAnimeSourceAdapter.adapt AniyomiMangaSourceAdapter.adapt
                    preferenceBridge.project(source)
                    inventory.discover(extension.packageName())
                    ApkExtensionRuntimeReport.activationFailed
                    """);
            Path main = repository.resolve(
                    "Anilib/Platforms/Android/src/main/kotlin/fr/vriege/anilib/platform/android/MainActivity.kt");
            write(main, """
                    AndroidAniyomiSourceRuntime(this).prepare()
                    PortableBundleLoading.DISABLED
                    AnilibStartupScreen(
                    apkActivation.bundles
                    startupReports = apkActivation.reports
                    """);
            Path workflow = repository.resolve(".github/workflows/android-release.yml");
            write(workflow, """
                    ubuntu-24.04 actions/checkout@v6.0.2 actions/setup-java@v5.6.0
                    java-version: 21.0.10 sdkmanager --install "platforms;android-37"
                    writeAndroidReleaseChecksums actions/upload-artifact@v7.0.1
                    """);
            AndroidReleaseRule rule = new AndroidReleaseRule();
            RepositorySnapshot snapshot = snapshot(repository);
            check(rule.analyze(snapshot).isEmpty(),
                    "a complete Android APK release contract must pass");
            Files.writeString(workflow, "ubuntu-24.04", StandardCharsets.UTF_8);
            check(rule.analyze(snapshot).stream()
                            .anyMatch(diagnostic -> diagnostic.message().contains("writeAndroidReleaseChecksums")),
                    "a missing APK task must produce an actionable diagnostic");
            write(workflow, """
                    ubuntu-24.04 actions/checkout@v6.0.2 actions/setup-java@v5.6.0
                    java-version: 21.0.10 sdkmanager --install "platforms;android-37"
                    writeAndroidReleaseChecksums actions/upload-artifact@v7.0.1
                    """);
            Files.writeString(manifest, "android:exported=\"false\"", StandardCharsets.UTF_8);
            check(rule.analyze(snapshot).stream()
                            .anyMatch(diagnostic -> diagnostic.message().contains("usesCleartextTraffic")),
                    "an insecure manifest change must produce an actionable diagnostic");
            write(manifest, """
                    android:usesCleartextTraffic="false" android:exported="false"
                    android.permission.QUERY_ALL_PACKAGES
                    """);
            check(rule.analyze(snapshot).stream()
                            .anyMatch(diagnostic -> diagnostic.message().contains("broad package visibility")),
                    "Android discovery must not gain unrestricted package visibility");
            write(manifest, """
                    android:usesCleartextTraffic="false" android:exported="false"
                    """);
            write(preflight, "Class.forName(className, false, applicationContext.classLoader)");
            check(rule.analyze(snapshot).stream()
                            .anyMatch(diagnostic -> diagnostic.message().contains(
                                    "extension.signingCertificateSha256()::contains")),
                    "APK runtime preflight must retain certificate binding before activation");
            write(preflight, """
                    extension.signingCertificateSha256()::contains
                    Class.forName(className, false, applicationContext.classLoader)
                    ApkExtensionRuntimeState.HOST_ABI_MISSING
                    ANIME_HOST_CLASSES MANGA_HOST_CLASSES
                    """);
            write(main, """
                    AndroidAniyomiSourceRuntime(this).prepare()
                    AnilibStartupScreen(
                    apkActivation.bundles
                    startupReports = apkActivation.reports
                    """);
            check(rule.analyze(snapshot).stream()
                            .anyMatch(diagnostic -> diagnostic.message().contains("PortableBundleLoading.DISABLED")),
                    "Android startup must not invoke the desktop JPMS Bundle loader");
            write(sourceRuntime, "PathClassLoader preflight.report(extension) AniyomiMangaSourceAdapter.adapt");
            check(rule.analyze(snapshot).stream()
                            .anyMatch(diagnostic -> diagnostic.message().contains("AniyomiAnimeSourceAdapter.adapt")),
                    "APK activation must retain its explicit Source adapter boundary");
            return 7;
        } catch (IOException exception) {
            throw new AssertionError("Unable to run Android release rule test", exception);
        } finally {
            deleteDirectory(repository);
        }
    }

    private static RepositorySnapshot snapshot(Path root) {
        return new RepositorySnapshot(root, List.of(), List.of(), List.of(), List.of());
    }

    private static Path temporaryDirectory() {
        try {
            return Files.createTempDirectory("anilib-android-release-rule");
        } catch (IOException exception) {
            throw new AssertionError("Unable to create Android release rule directory", exception);
        }
    }

    private static void write(Path path, String content) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, content, StandardCharsets.UTF_8);
    }

    private static void deleteDirectory(Path directory) {
        if (!Files.exists(directory)) {
            return;
        }
        try (java.util.stream.Stream<Path> entries = Files.walk(directory)) {
            for (Path entry : entries.sorted(java.util.Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(entry);
            }
        } catch (IOException exception) {
            throw new AssertionError("Unable to clean Android release rule directory", exception);
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
