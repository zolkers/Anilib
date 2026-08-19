package fr.vriege.anilib.tooling.archtests;

import fr.vriege.anilib.tooling.extensionportability.ExtensionPortability;
import fr.vriege.anilib.tooling.extensionportability.PortabilityReport;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

final class ExtensionPortabilityTest {
    private ExtensionPortabilityTest() {
    }

    static int run() {
        Path directory = temporaryDirectory();
        try {
            Path repository = directory.resolve("source-repository");
            Path source = repository.resolve("src/main/kotlin/Example.kt");
            Files.createDirectories(source.getParent());
            Files.writeString(source, """
                    package example

                    import android.webkit.WebView
                    import eu.kanade.tachiyomi.animesource.AnimeSource
                    import java.nio.file.Files
                    import okhttp3.OkHttpClient

                    val namespace = "eu.kanade.tachiyomi.animeextension.en.example"
                    override val id: Long = 9223372036854775807L
                    val torrentClient = "libtorrent"
                    """, StandardCharsets.UTF_8);
            Files.writeString(repository.resolve("build.gradle.kts"),
                    "compileOnly(\"host:source-api:1\")\n", StandardCharsets.UTF_8);

            PortabilityReport report = ExtensionPortability.analyze(repository);
            check(report.packageIdentity().orElseThrow()
                            .equals("eu.kanade.tachiyomi.animeextension.en.example"),
                    "portability analysis must preserve the detected package identity");
            check(report.sourceIds().equals(List.of("9223372036854775807")),
                    "portability analysis must preserve detected source IDs");
            Set<String> categories = report.findings().stream()
                    .map(finding -> finding.category())
                    .collect(Collectors.toSet());
            check(categories.containsAll(Set.of(
                            "ANDROID_SDK",
                            "ANIYOMI_HOST_ABI",
                            "COMPILE_ONLY_HOST_DEPENDENCY",
                            "WEBVIEW",
                            "TORRENT",
                            "DIRECT_NETWORK",
                            "DIRECT_STORAGE")),
                    "portability analysis must classify the strategy risk categories");

            Path reports = directory.resolve("reports");
            ExtensionPortability.writeReports(report, reports);
            String json = Files.readString(reports.resolve("portability-report.json"), StandardCharsets.UTF_8);
            check(json.contains("\"packageIdentity\": \"eu.kanade.tachiyomi.animeextension.en.example\"")
                            && Files.isRegularFile(reports.resolve("portability-report.md")),
                    "portability analysis must emit machine-readable and human-readable reports");

            Path scaffold = directory.resolve("generated-module");
            ExtensionPortability.scaffold(report, scaffold, "anime", "en");
            String descriptor = Files.readString(
                    scaffold.resolve("src/main/resources/META-INF/anilib-extension.properties"),
                    StandardCharsets.UTF_8);
            String publisher = Files.readString(
                    scaffold.resolve("source-publisher.properties"),
                    StandardCharsets.UTF_8);
            check(descriptor.contains("package=eu.kanade.tachiyomi.animeextension.en.example")
                            && descriptor.contains("source.0.id=9223372036854775807")
                            && publisher.contains("kind=anime"),
                    "generated Anilib modules must preserve package and source identities");
            String moduleInfo = Files.readString(
                    scaffold.resolve("src/main/java/module-info.java"),
                    StandardCharsets.UTF_8);
            check(moduleInfo.contains("exports fr.vriege.anilib.extension.ported."),
                    "generated implementation packages must use the fr.vriege.anilib namespace");
            return 5;
        } catch (IOException exception) {
            throw new AssertionError("Unable to verify extension portability tooling", exception);
        } finally {
            deleteDirectory(directory);
        }
    }

    private static Path temporaryDirectory() {
        try {
            return Files.createTempDirectory("anilib-extension-portability-test");
        } catch (IOException exception) {
            throw new AssertionError("Unable to create extension portability test directory", exception);
        }
    }

    private static void deleteDirectory(Path directory) {
        if (!Files.exists(directory)) {
            return;
        }
        try (java.util.stream.Stream<Path> entries = Files.walk(directory)) {
            for (Path entry : entries.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(entry);
            }
        } catch (IOException exception) {
            throw new AssertionError("Unable to clean extension portability test directory", exception);
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
