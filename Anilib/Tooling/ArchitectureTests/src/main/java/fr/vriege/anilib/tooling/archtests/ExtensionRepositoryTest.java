package fr.vriege.anilib.tooling.archtests;

import fr.vriege.anilib.feature.extensionrepository.ExtensionArtifactFormat;
import fr.vriege.anilib.feature.extensionrepository.ExtensionArtifactMetadata;
import fr.vriege.anilib.feature.extensionrepository.ExtensionBrowsePreferences;
import fr.vriege.anilib.feature.extensionrepository.ExtensionContentKind;
import fr.vriege.anilib.feature.extensionrepository.ExtensionInstallationState;
import fr.vriege.anilib.feature.extensionrepository.ExtensionPackageMetadata;
import fr.vriege.anilib.feature.extensionrepository.ExtensionPlatformAvailability;
import fr.vriege.anilib.feature.extensionrepository.ExtensionRepositorySnapshot;
import fr.vriege.anilib.feature.extensionrepository.ExtensionRepositoryService;
import fr.vriege.anilib.feature.extensionrepository.ExtensionSourceMetadata;
import fr.vriege.anilib.feature.extensionrepository.InstalledExtensionPackage;
import fr.vriege.anilib.feature.extensionrepository.runtime.AniyomiRepositoryIndexParser;
import fr.vriege.anilib.feature.extensionrepository.runtime.AniyomiAnimeSourceAdapter;
import fr.vriege.anilib.feature.extensionrepository.runtime.AniyomiMangaSourceAdapter;
import fr.vriege.anilib.feature.extensionrepository.runtime.AniyomiSourcePreferences;
import fr.vriege.anilib.feature.extensionrepository.runtime.DefaultExtensionInstallationService;
import fr.vriege.anilib.feature.extensionrepository.runtime.DefaultExtensionRepositoryService;
import fr.vriege.anilib.feature.extensionrepository.runtime.DefaultExtensionUpdateService;
import fr.vriege.anilib.feature.extensionrepository.runtime.FileExtensionBrowsePreferenceStore;
import fr.vriege.anilib.feature.extensionrepository.runtime.FileExtensionTrustStore;
import fr.vriege.anilib.feature.extensionrepository.runtime.FileExtensionRepositoryStore;
import fr.vriege.anilib.feature.extensionrepository.runtime.FileInstalledExtensionStore;
import fr.vriege.anilib.feature.extensionrepository.runtime.FileExtensionUpdatePolicyStore;
import fr.vriege.anilib.feature.extensionrepository.runtime.MiwayomiSourceBridge;
import fr.vriege.anilib.feature.extensionrepository.ui.ApkExtensionCompatibility;
import fr.vriege.anilib.feature.extensionrepository.ui.ApkExtensionPlatforms;
import fr.vriege.anilib.feature.extensionrepository.ui.ApkExtensionRuntimeReport;
import fr.vriege.anilib.feature.extensionrepository.ui.ApkExtensionRuntimeState;
import fr.vriege.anilib.feature.extensionrepository.ui.InstalledApkExtension;
import fr.vriege.anilib.framework.http.AnilibHttpClient;
import fr.vriege.anilib.framework.http.HttpRequest;
import fr.vriege.anilib.framework.http.HttpResponse;
import fr.vriege.anilib.feature.source.CatalogueSource;
import fr.vriege.anilib.feature.source.PagedSource;
import fr.vriege.anilib.feature.source.SourceBrowseRequest;
import fr.vriege.anilib.feature.source.SourceContentKind;
import fr.vriege.anilib.feature.source.SourceEpisode;
import fr.vriege.anilib.feature.source.SourceFilterType;
import fr.vriege.anilib.feature.source.SourceFilterValue;
import fr.vriege.anilib.feature.source.SourcePage;
import fr.vriege.anilib.feature.source.SourcePermission;
import fr.vriege.anilib.feature.source.SourcePageResource;
import fr.vriege.anilib.feature.source.SourcePreferenceDefinition;
import fr.vriege.anilib.feature.source.SourcePreferenceType;
import fr.vriege.anilib.feature.source.SourceSearchRequest;
import fr.vriege.anilib.feature.source.StreamingSource;
import fr.vriege.anilib.feature.source.SourceCapabilities;
import fr.vriege.anilib.feature.source.SourceRegistry;
import fr.vriege.anilib.feature.source.bundle.SourceSdkPlugin;
import fr.vriege.anilib.kernel.AnilibPlugin;
import fr.vriege.anilib.kernel.StartedAnilib;
import fr.vriege.anilib.kernel.runtime.DefaultPluginEngine;

import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.Signature;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.Base64;
import java.util.HexFormat;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

final class ExtensionRepositoryTest {
    private static final URI INDEX = URI.create("https://repo.example/extensions/index.min.json");
    private static final URI BUNDLE = URI.create("https://repo.example/extensions/example.jar");
    private static final String SHA_256 = "0123456789abcdef0123456789abcdef"
            + "0123456789abcdef0123456789abcdef";

    private ExtensionRepositoryTest() {
    }

    static int run() {
        Counter counter = new Counter();
        parsesAniyomiAndPortableArtifacts(counter);
        selectsArtifactsByHostPlatform(counter);
        parsesPublicRepositoryShapes(counter);
        rejectsUnsafeMetadata(counter);
        persistsAndRefreshesUserRepositories(counter);
        persistsBrowsePreferences(counter);
        resolvesGitHubRepositoriesDynamically(counter);
        installsOnlyTrustedPortableBundles(counter);
        rejectsAdultSourcesWhenDisabled(counter);
        updatesInstalledPortableSources(counter);
        modelsInstalledApkDiscovery(counter);
        adaptsAbiReadyAnimeSource(counter);
        adaptsModernSuspendAndHosterAnimeSource(counter);
        adaptsModernMangaSource(counter);
        adaptsConfigurableAnimeSourcePreferences(counter);
        bridgesDesktopApkSourcesThroughLoopback(counter);
        return counter.value;
    }

    private static void bridgesDesktopApkSourcesThroughLoopback(Counter counter) {
        MiwayomiBridgeClient client = new MiwayomiBridgeClient();
        MiwayomiSourceBridge bridge = new MiwayomiSourceBridge(URI.create("http://127.0.0.1:43127/"), client);
        bridge.requireHealthy();
        bridge.saveRepositories(List.of(INDEX));
        List<AnilibPlugin> bundles = bridge.sourceBundles();
        List<AnilibPlugin> plugins = new java.util.ArrayList<>();
        plugins.add(new SourceSdkPlugin());
        plugins.addAll(bundles);
        try (StartedAnilib started = new DefaultPluginEngine().start(plugins)) {
            SourceRegistry registry = started.capability(SourceCapabilities.REGISTRY);
            counter.check(registry.sources().size() == 2
                            && registry.find(fr.vriege.anilib.feature.source.SourceId.of("aniyomi.42")).isPresent()
                            && registry.find(fr.vriege.anilib.feature.source.SourceId.of("aniyomi.43")).isPresent(),
                    "the desktop engine must publish manga and anime APK sources as explicit Source Bundles");

            CatalogueSource mangaCatalogue = (CatalogueSource) registry.find(
                    fr.vriege.anilib.feature.source.SourceId.of("aniyomi.42")).orElseThrow();
            SourcePage mangaPage = mangaCatalogue.popular(new SourceBrowseRequest(1, 20, List.of(), Map.of()));
            PagedSource manga = (PagedSource) mangaCatalogue;
            var chapters = manga.contentUnits(mangaPage.items().getFirst().id());
            var pages = manga.pages(chapters.getFirst().id());
            counter.check(mangaPage.items().getFirst().title().equals("Bridge Manga")
                            && chapters.getFirst().title().equals("Chapter 1")
                            && new String(manga.readPage(pages.getFirst()), StandardCharsets.UTF_8).equals("image"),
                    "the desktop manga bridge must map catalogue, chapters, pages, and proxied bytes");

            CatalogueSource animeCatalogue = (CatalogueSource) registry.find(
                    fr.vriege.anilib.feature.source.SourceId.of("aniyomi.43")).orElseThrow();
            SourcePage animePage = animeCatalogue.search(new SourceSearchRequest(
                    "bridge",
                    new SourceBrowseRequest(1, 20, List.of(), Map.of())));
            StreamingSource anime = (StreamingSource) animeCatalogue;
            var episodes = anime.episodes(animePage.items().getFirst().id());
            var streams = anime.streams(episodes.getFirst().id());
            counter.check(episodes.getFirst().title().equals("Episode 1")
                            && streams.getFirst().location().equals(URI.create("https://cdn.example/master.m3u8"))
                            && streams.getFirst().format().name().equals("HLS")
                            && streams.getFirst().headers().get("Referer").equals("https://source.example/")
                            && streams.getFirst().subtitles().size() == 1,
                    "the desktop anime bridge must hand original streams and headers to Anilib's media relay");
        }
        String installed = bridge.install(URI.create("https://repo.example/extensions/example.apk"));
        counter.check(
                client.savedRepositories && client.installRequested && installed.contains("installed for desktop"),
                "desktop APK installation must synchronize repositories and report immediate installation");
        counter.expectIllegalArgument(
                () -> new MiwayomiSourceBridge(URI.create("http://example.test:43127/"), client),
                "the desktop APK bridge must reject non-loopback engines");
    }

    private static void selectsArtifactsByHostPlatform(Counter counter) {
        AniyomiRepositoryIndexParser parser = new AniyomiRepositoryIndexParser();
        ExtensionPackageMetadata apkOnly = parser.parse(INDEX, """
                [{"name":"APK","pkg":"vendor.apk","apk":"only.apk","lang":"en",
                "code":1,"version":"1","sources":[{"name":"APK","lang":"en","id":"1"}]}]
                """).getFirst();
        ExtensionPlatformAvailability apkAvailability = ExtensionPlatformAvailability.from(apkOnly);
        counter.check(apkAvailability.android() && !apkAvailability.desktop()
                        && apkAvailability.androidArtifact().orElseThrow().format()
                        == ExtensionArtifactFormat.ANIYOMI_APK,
                "APK-only packages must be Android-only");

        ExtensionPackageMetadata portableOnly = parser.parse(INDEX, """
                [{"name":"Bundle","pkg":"vendor.bundle","lang":"en","code":1,"version":"1",
                "anilib":{"bundle":"bundle.jar","api":"1.6","sha256":"%s",
                "signature":"c2ln","keyId":"publisher","kind":"manga"},
                "sources":[{"name":"Bundle","lang":"en","id":"2"}]}]
                """.formatted(SHA_256)).getFirst();
        ExtensionPlatformAvailability portableAvailability = ExtensionPlatformAvailability.from(portableOnly);
        counter.check(portableAvailability.android() && portableAvailability.desktop()
                        && portableAvailability.androidArtifact().orElseThrow().format()
                        == ExtensionArtifactFormat.ANILIB_BUNDLE,
                "portable-only packages must select the Bundle on Android and desktop");

        String dualJson = """
                [{"name":"Dual","pkg":"vendor.dual","apk":"fallback.apk","lang":"en",
                "code":1,"version":"1","anilib":{"bundle":"dual.jar","api":"1.6",
                "sha256":"%s","signature":"c2ln","keyId":"publisher","kind":"anime"},
                "sources":[{"name":"Dual","lang":"en","id":"3"}]}]
                """.formatted(SHA_256);
        ExtensionPlatformAvailability dualAvailability = ExtensionPlatformAvailability.from(
                parser.parse(INDEX, dualJson).getFirst());
        counter.check(dualAvailability.androidArtifact().orElseThrow().format()
                        == ExtensionArtifactFormat.ANILIB_BUNDLE
                        && dualAvailability.desktopArtifact().orElseThrow().format()
                        == ExtensionArtifactFormat.ANILIB_BUNDLE,
                "dual packages must prefer the portable Bundle on every host");
        counter.expectIllegalArgument(
                () -> parser.parse(INDEX, """
                        [{"name":"Invalid","pkg":"vendor.invalid","lang":"en","code":1,"version":"1",
                        "sources":[{"name":"Invalid","lang":"en","id":"4"}]}]
                        """),
                "packages without an APK or portable Bundle must be rejected");
    }

    private static void parsesAniyomiAndPortableArtifacts(Counter counter) {
        String index = """
                [{
                  "name":"Aniyomi: Example",
                  "pkg":"vendor:any/pkg@v1",
                  "apk":"example-v14.2.apk",
                  "lang":"en",
                  "code":2,
                  "version":"14.2",
                  "changelog":"Improved source compatibility.",
                  "nsfw":0,
                  "anilib":{
                    "bundle":"example-v1.2.jar",
                    "api":"1.4",
                    "sha256":"%s",
                    "signature":"c2lnbmF0dXJl",
                    "keyId":"example-key",
                    "kind":"anime"
                  },
                  "sources":[{
                    "name":"Example",
                    "lang":"en",
                    "id":"1234567890123456789",
                    "baseUrl":""
                  }]
                }]
                """.formatted(SHA_256);
        List<ExtensionPackageMetadata> packages = new AniyomiRepositoryIndexParser().parse(INDEX, index);
        ExtensionPackageMetadata extension = packages.getFirst();
        counter.check(extension.packageName().equals("vendor:any/pkg@v1"),
                "pkg must remain an opaque publisher identity without a vendor-prefix restriction");
        counter.check(extension.artifacts().size() == 2,
                "one compatible index entry must expose APK and portable Bundle artifacts");
        counter.check(extension.artifacts().getFirst().format() == ExtensionArtifactFormat.ANIYOMI_APK,
                "Aniyomi apk metadata must remain identifiable");
        counter.check(extension.artifacts().get(1).sha256().orElseThrow().equals(SHA_256),
                "portable Bundle metadata must retain its checksum");
        counter.check(extension.sources().getFirst().baseUri().isEmpty(),
                "Aniyomi indexes may advertise a source with an empty baseUrl");
        counter.check(extension.changelog().orElseThrow().equals("Improved source compatibility."),
                "repository entries must retain optional release changelogs");
        counter.check(extension.icon().orElseThrow().equals(
                        URI.create("https://repo.example/extensions/icon/vendor%3Aany%2Fpkg%40v1.png")),
                "Aniyomi extension icons must resolve through the repository icon directory");
    }

    private static void parsesPublicRepositoryShapes(Counter counter) {
        URI yuzonoIndex = URI.create(
                "https://raw.githubusercontent.com/yuzono/anime-repo/repo/index.min.json");
        String yuzonoShape = """
                [{
                  "name":"Synthetic Anime Fixture",
                  "pkg":"eu.kanade.tachiyomi.animeextension.en.synthetic",
                  "apk":"aniyomi-en.synthetic-v14.7.apk",
                  "lang":"en",
                  "code":7,
                  "version":"14.7",
                  "nsfw":0,
                  "sources":[{
                    "name":"Synthetic Anime Source",
                    "lang":"en",
                    "id":"92233720368547758070",
                    "baseUrl":"https://anime.example.test"
                  }]
                }]
                """;
        ExtensionPackageMetadata anime = new AniyomiRepositoryIndexParser()
                .parse(yuzonoIndex, yuzonoShape)
                .getFirst();
        counter.check(anime.contentKind() == ExtensionContentKind.ANIME
                        && anime.sources().getFirst().sourceId().equals("92233720368547758070"),
                "the Yuzono JSON shape must retain anime identity and unsigned 64-bit source ids");
        counter.check(anime.artifacts().getFirst().uri().equals(URI.create(
                        "https://raw.githubusercontent.com/yuzono/anime-repo/repo/apk/"
                                + "aniyomi-en.synthetic-v14.7.apk")),
                "Yuzono APK filenames must resolve through the public repository apk directory");

        URI keiyoushiIndex = URI.create(
                "https://raw.githubusercontent.com/keiyoushi/extensions/repo/index.min.json");
        String keiyoushiShape = """
                [{
                  "name":"Synthetic Manga Fixture",
                  "pkg":"eu.kanade.tachiyomi.extension.all.synthetic",
                  "apk":"tachiyomi-all.synthetic-v1.4.1.apk",
                  "lang":"all",
                  "code":1,
                  "version":"1.4.1",
                  "nsfw":false,
                  "sources":[
                    {"name":"Synthetic Manga","lang":"all","id":"1","baseUrl":""},
                    {"name":"Synthetic Manga","lang":"pt-BR","id":"2",
                     "baseUrl":"https://manga.example.test"}
                  ]
                }]
                """;
        ExtensionPackageMetadata manga = new AniyomiRepositoryIndexParser()
                .parse(keiyoushiIndex, keiyoushiShape)
                .getFirst();
        counter.check(manga.contentKind() == ExtensionContentKind.MANGA
                        && manga.languageTag().equals("und")
                        && manga.sources().get(1).languageTag().equals("pt-br"),
                "the Keiyoushi JSON shape must normalize all and regional language tags");
        counter.check(manga.artifacts().getFirst().uri().equals(URI.create(
                        "https://raw.githubusercontent.com/keiyoushi/extensions/repo/apk/"
                                + "tachiyomi-all.synthetic-v1.4.1.apk")),
                "Keiyoushi APK filenames must resolve through the public repository apk directory");
    }

    private static void rejectsUnsafeMetadata(Counter counter) {
        AniyomiRepositoryIndexParser parser = new AniyomiRepositoryIndexParser();
        counter.expectIllegalArgument(
                () -> parser.parse(URI.create("http://repo.example/index.json"), "[]"),
                "repository indexes must require HTTPS");
        counter.expectIllegalArgument(
                () -> parser.parse(INDEX, """
                        [{"name":"Bad","pkg":"invalid","apk":"bad.apk","lang":"en",
                        "code":1,"version":"1","sources":[]}]
                        """),
                "repository entries must reject empty source declarations");
        counter.expectIllegalArgument(
                () -> parser.parse(INDEX, """
                        [{"name":"Bad","pkg":"line\\nbreak","apk":"bad.apk","lang":"en",
                        "code":1,"version":"1","sources":[{"name":"Bad","lang":"en","id":"1"}]}]
                        """),
                "repository package identities must reject control characters");
        counter.expectIllegalArgument(
                () -> parser.parse(INDEX, """
                        [{"name":"Bad","pkg":"a.b","apk":"http://bad.example/a.apk","lang":"en",
                        "code":1,"version":"1","sources":[{"name":"Bad","lang":"en","id":"1"}]}]
                        """),
                "repository artifacts must reject cleartext downloads");
    }

    private static void persistsAndRefreshesUserRepositories(Counter counter) {
        Path directory = temporaryDirectory();
        try {
            Path storePath = directory.resolve("repositories.txt");
            String index = """
                    [{"name":"Example","pkg":"eu.example.extension","apk":"example.apk",
                    "lang":"all","code":4,"version":"1.4","nsfw":1,
                    "sources":[{"name":"Example","lang":"all","id":"42",
                    "baseUrl":"https://source.example"}]}]
                    """;
            RecordingClient client = new RecordingClient(index);
            DefaultExtensionRepositoryService service = new DefaultExtensionRepositoryService(
                    new FileExtensionRepositoryStore(storePath),
                    client,
                    Clock.fixed(Instant.parse("2026-08-18T10:00:00Z"), ZoneOffset.UTC),
                    new AniyomiRepositoryIndexParser());
            counter.check(service.repositories().isEmpty(),
                    "new products must not receive a bundled third-party repository");
            service.add(INDEX);
            ExtensionRepositorySnapshot snapshot = service.refresh(INDEX);
            counter.check(snapshot.successful() && snapshot.packages().size() == 1,
                    "configured repository must refresh into a bounded catalogue");
            counter.check(client.lastRequest.uri().equals(INDEX)
                            && client.lastRequest.headers().get("accept").contains("application/json"),
                    "repository refresh must use the shared HTTP client and explicit JSON acceptance");
            counter.check(service.packages().getFirst().adult(),
                    "Aniyomi nsfw metadata must survive catalogue refresh");
            DefaultExtensionRepositoryService reopened = new DefaultExtensionRepositoryService(
                    new FileExtensionRepositoryStore(storePath),
                    client);
            counter.check(reopened.repositories().equals(List.of(INDEX)),
                    "user repository URLs must survive product restart");
            counter.check(reopened.remove(INDEX) && reopened.repositories().isEmpty(),
                    "users must be able to remove their own repository URL");
        } finally {
            deleteDirectory(directory);
        }
    }

    private static void persistsBrowsePreferences(Counter counter) {
        Path directory = temporaryDirectory();
        try {
            Path preferencesFile = directory.resolve("extension-browse.tsv");
            FileExtensionBrowsePreferenceStore store = new FileExtensionBrowsePreferenceStore(preferencesFile);
            store.save(new ExtensionBrowsePreferences(
                    Set.of("FR_fr", "en"),
                    Set.of("publisher:anime/source", "publisher:manga/source")));
            ExtensionBrowsePreferences reopened = new FileExtensionBrowsePreferenceStore(preferencesFile).snapshot();
            counter.check(reopened.enabledLanguages().equals(Set.of("fr-fr", "en")),
                    "extension language choices must be normalized and survive product restart");
            counter.check(reopened.pinnedPackages().equals(
                            Set.of("publisher:anime/source", "publisher:manga/source")),
                    "pinned extension packages must survive Android and desktop restart");
        } finally {
            deleteDirectory(directory);
        }
    }

    private static void resolvesGitHubRepositoriesDynamically(Counter counter) {
        Path directory = temporaryDirectory();
        try {
            URI github = URI.create("https://github.com/example/anilib-sources");
            String index = """
                    [{"name":"Portable","pkg":"fr.example.sources","lang":"all",
                    "code":1,"version":"1.0","nsfw":false,
                    "anilib":{"bundle":"portable.jar","api":"1.4","sha256":"%s",
                    "signature":"c2ln","keyId":"publisher","kind":"manga"},
                    "sources":[{"name":"Portable","lang":"all","id":"portable"}]}]
                    """.formatted(SHA_256);
            GitHubIndexClient client = new GitHubIndexClient(index);
            DefaultExtensionRepositoryService service = new DefaultExtensionRepositoryService(
                    new FileExtensionRepositoryStore(directory.resolve("repositories.txt")),
                    client);
            service.add(github);
            ExtensionRepositorySnapshot snapshot = service.refresh(github);
            counter.check(snapshot.successful() && snapshot.packages().size() == 1,
                    "a GitHub repository URL must resolve to its dynamic JSON index");
            counter.check(client.requests.equals(List.of(
                            URI.create("https://raw.githubusercontent.com/example/anilib-sources/HEAD/index.min.json"),
                            URI.create("https://raw.githubusercontent.com/example/anilib-sources/HEAD/index.json"),
                            URI.create("https://raw.githubusercontent.com/example/anilib-sources/repo/index.min.json"),
                            URI.create("https://raw.githubusercontent.com/example/anilib-sources/repo/index.json"))),
                    "GitHub resolution must search default and publication branches deterministically");
            counter.check(snapshot.packages().getFirst().artifacts().getFirst().uri().equals(
                            URI.create("https://raw.githubusercontent.com/example/anilib-sources/repo/portable.jar")),
                    "relative Bundle URLs must resolve beside the fetched GitHub index");
        } finally {
            deleteDirectory(directory);
        }
    }

    private static void installsOnlyTrustedPortableBundles(Counter counter) {
        Path directory = temporaryDirectory();
        try {
            KeyPair keyPair = keyPair();
            String packageId = "vendor:any/pkg@v1";
            byte[] versionOne = bundle(packageId, 1, "1.4");
            ExtensionPackageMetadata first = portablePackage(packageId, versionOne, keyPair, 1);
            DefaultExtensionInstallationService service = installationService(
                    directory,
                    new RecordingClient(versionOne));
            service.trust("example-publisher", Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded()));
            InstalledExtensionPackage installed = service.install(first);
            counter.check(installed.state() == ExtensionInstallationState.ENABLED
                            && service.installed().equals(List.of(installed)),
                    "a checksum-matched Bundle signed by a trusted publisher must install enabled");
            service.setEnabled(first.packageName(), false);
            counter.check(service.installed().getFirst().state() == ExtensionInstallationState.DISABLED,
                    "installed portable Bundles must support durable disable state");

            byte[] versionTwo = bundle(packageId, 2, "1.4");
            DefaultExtensionInstallationService reopened = installationService(
                    directory,
                    new RecordingClient(versionTwo));
            InstalledExtensionPackage updated = reopened.update(portablePackage(
                    packageId,
                    versionTwo,
                    keyPair,
                    2));
            counter.check(updated.versionCode() == 2
                            && updated.state() == ExtensionInstallationState.DISABLED,
                    "a verified newer Bundle must update without silently enabling a disabled extension");
            counter.check(reopened.remove(first.packageName()) && reopened.installed().isEmpty(),
                    "users must be able to remove installed portable Bundles");

            Path untrustedDirectory = directory.resolve("untrusted");
            DefaultExtensionInstallationService untrusted = installationService(
                    untrustedDirectory,
                    new RecordingClient(versionOne));
            counter.expectSecurity(
                    () -> untrusted.install(first),
                    "portable Bundles from untrusted signing keys must not install");
            counter.check(new DefaultExtensionInstallationService(
                            directory,
                            new RecordingClient(versionTwo)).installed().isEmpty(),
                    "installed-extension removal must survive product restart");
        } finally {
            deleteDirectory(directory);
        }
    }

    private static DefaultExtensionInstallationService installationService(
            Path directory,
            RecordingClient client) {
        return new DefaultExtensionInstallationService(
                directory,
                client,
                Clock.fixed(Instant.parse("2026-08-18T12:00:00Z"), ZoneOffset.UTC),
                new FileInstalledExtensionStore(directory.resolve("installed.tsv")),
                new FileExtensionTrustStore(directory.resolve("trusted-keys.txt")));
    }

    private static void rejectsAdultSourcesWhenDisabled(Counter counter) {
        Path directory = temporaryDirectory();
        try {
            KeyPair publisher = keyPair();
            byte[] archive = bundle("publisher:adult/source", 1, "1.4");
            ExtensionPackageMetadata base = portablePackage("publisher:adult/source", archive, publisher, 1);
            ExtensionPackageMetadata adult = new ExtensionPackageMetadata(
                    base.displayName(),
                    base.packageName(),
                    base.languageTag(),
                    base.versionCode(),
                    base.versionName(),
                    true,
                    base.contentKind(),
                    base.sources(),
                    base.artifacts());
            DefaultExtensionInstallationService service = new DefaultExtensionInstallationService(
                    directory,
                    new RecordingClient(archive),
                    Clock.fixed(Instant.parse("2026-08-18T12:00:00Z"), ZoneOffset.UTC),
                    new FileInstalledExtensionStore(directory.resolve("installed.tsv")),
                    new FileExtensionTrustStore(directory.resolve("trusted-keys.txt")),
                    List.of(),
                    () -> false);
            counter.expectSecurity(
                    () -> service.install(adult),
                    "adult source packages must remain unavailable while their Settings policy is disabled");
        } finally {
            deleteDirectory(directory);
        }
    }

    private static void updatesInstalledPortableSources(Counter counter) {
        Path directory = temporaryDirectory();
        try {
            KeyPair publisher = keyPair();
            String packageId = "publisher:catalogue/source";
            byte[] versionOne = bundle(packageId, 1, "1.4");
            DefaultExtensionInstallationService initial = installationService(
                    directory,
                    new RecordingClient(versionOne));
            initial.trust(
                    "example-publisher",
                    Base64.getEncoder().encodeToString(publisher.getPublic().getEncoded()));
            initial.install(portablePackage(packageId, versionOne, publisher, 1));

            byte[] versionTwo = bundle(packageId, 2, "1.4");
            DefaultExtensionInstallationService installation = installationService(
                    directory,
                    new RecordingClient(versionTwo));
            MutableRepositoryService repository = new MutableRepositoryService(
                    portablePackage(packageId, versionTwo, publisher, 2));
            Path policyFile = directory.resolve("automatic-updates.properties");
            try (DefaultExtensionUpdateService updates = new DefaultExtensionUpdateService(
                    repository,
                    installation,
                    new FileExtensionUpdatePolicyStore(policyFile))) {
                counter.check(updates.availableUpdates().size() == 1
                                && updates.availableUpdates().getFirst().automaticEligible(),
                        "a newer Bundle signed by the installed publisher must enter the automatic channel");
                counter.check(updates.updateAllAvailable().updated().getFirst().versionCode() == 2,
                        "the shared update channel must verify and install all available updates");
                updates.setAutomaticUpdatesEnabled(true);
                counter.check(new FileExtensionUpdatePolicyStore(policyFile).load(),
                        "automatic source-update opt-in must survive Android and desktop restart");
            }
        } finally {
            deleteDirectory(directory);
        }
    }

    private static void modelsInstalledApkDiscovery(Counter counter) {
        InstalledApkExtension extension = new InstalledApkExtension(
                "eu.kanade.tachiyomi.animeextension.en.example",
                "Example",
                7,
                "16.7",
                "16.0",
                false,
                false,
                ExtensionContentKind.ANIME,
                List.of("eu.kanade.tachiyomi.animeextension.en.example.Example"),
                Optional.of("eu.kanade.tachiyomi.animeextension.en.example.ExampleFactory"),
                true,
                true,
                List.of(SHA_256),
                ApkExtensionCompatibility.COMPATIBLE_METADATA);
        counter.check(extension.sourceEntrypoints().size() == 1
                        && extension.sourceFactory().isPresent()
                        && extension.hasReadme()
                        && extension.contentKind() == ExtensionContentKind.ANIME
                        && extension.compatibility() == ApkExtensionCompatibility.COMPATIBLE_METADATA,
                "Android discovery metadata must retain the external APK extension contract and media kind");
        counter.check(ApkExtensionPlatforms.unavailable().discoverInstalled().isEmpty(),
                "platforms without APK support must expose an empty APK inventory");
        ApkExtensionRuntimeReport preflight = new ApkExtensionRuntimeReport(
                extension.packageName(),
                ApkExtensionRuntimeState.HOST_ABI_MISSING,
                List.of("rx.Observable", "eu.kanade.tachiyomi.animesource.AnimeSource"),
                Optional.of(SHA_256),
                Optional.empty());
        counter.check(preflight.missingHostClasses().getFirst()
                        .equals("eu.kanade.tachiyomi.animesource.AnimeSource")
                        && preflight.trustedCertificateSha256().orElseThrow().equals(SHA_256),
                "APK runtime preflight must retain deterministic ABI and certificate evidence");
        counter.check(ApkExtensionPlatforms.unavailable().runtimeReport(extension).state()
                        == ApkExtensionRuntimeState.UNSUPPORTED_PLATFORM,
                "platforms without an APK runtime must report it explicitly");
        ApkExtensionRuntimeReport failed = ApkExtensionRuntimeReport.activationFailed(
                extension.packageName(),
                SHA_256,
                "LinkageError: missing ABI method");
        counter.check(failed.state() == ApkExtensionRuntimeState.ACTIVATION_FAILED
                        && failed.activationFailure().orElseThrow().contains("missing ABI"),
                "APK activation failures must remain visible without hiding certificate trust");
        counter.expectIllegalArgument(
                () -> new ApkExtensionRuntimeReport(
                        extension.packageName(),
                        ApkExtensionRuntimeState.HOST_ABI_MISSING,
                        List.of(),
                        Optional.of(SHA_256),
                        Optional.empty()),
                "missing-host-ABI reports must identify at least one absent class");
        counter.expectIllegalArgument(
                () -> new InstalledApkExtension(
                        " ",
                        "Example",
                        1,
                        "16.1",
                        "16.0",
                        false,
                        false,
                        ExtensionContentKind.MANGA,
                        List.of(),
                        Optional.empty(),
                        false,
                        false,
                        List.of(),
                        ApkExtensionCompatibility.MISSING_ENTRYPOINT),
                "APK extension metadata must reject blank package identities");
    }

    private static void adaptsAbiReadyAnimeSource(Counter counter) {
        AtomicBoolean authorized = new AtomicBoolean(true);
        AniyomiAnimeSourceAdapter.AdaptedSource adapted = AniyomiAnimeSourceAdapter.adapt(
                "eu.kanade.tachiyomi.animeextension.en.example",
                "16.7",
                new AniyomiAdapterFixture.Source(),
                authorized::get);
        CatalogueSource catalogue = (CatalogueSource) adapted.source();
        SourcePage page = catalogue.popular(new SourceBrowseRequest(1, 20, List.of(), Map.of()));
        counter.check(page.items().size() == 1
                        && page.hasNextPage()
                        && page.items().getFirst().title().equals("Example Anime"),
                "an ABI-ready APK source must adapt its catalogue page into Anilib models");
        StreamingSource streaming = (StreamingSource) adapted.source();
        List<SourceEpisode> episodes = streaming.episodes(page.items().getFirst().id());
        counter.check(episodes.size() == 1 && episodes.getFirst().episodeNumber() == 1.0d,
                "an adapted APK source must retain episode identity and ordering");
        var streams = streaming.streams(episodes.getFirst().id());
        counter.check(streams.size() == 1
                        && streams.getFirst().format().name().equals("HLS")
                        && streams.getFirst().headers().get("Referer").equals("https://example.test/")
                        && streams.getFirst().subtitles().size() == 1,
                "an adapted APK source must retain stream, header, format, and subtitle metadata");
        counter.check(adapted.bundle().manifest().descriptor().id().toString()
                        .startsWith("extension.aniyomi."),
                "an adapted APK source must become one explicit Source Bundle");
        counter.check(adapted.manifest().permissions().equals(Set.of(SourcePermission.TRUSTED_PLATFORM_RUNTIME)),
                "the APK adapter must declare its audited platform-runtime exception explicitly");
        authorized.set(false);
        counter.expectSecurity(
                () -> catalogue.popular(new SourceBrowseRequest(1, 20, List.of(), Map.of())),
                "revoking APK certificate trust must block subsequent adapted source calls");
    }

    private static void adaptsModernSuspendAndHosterAnimeSource(Counter counter) {
        AniyomiAdapterFixture.ModernSource modernSource = new AniyomiAdapterFixture.ModernSource();
        AniyomiAnimeSourceAdapter.AdaptedSource adapted = AniyomiAnimeSourceAdapter.adapt(
                "eu.kanade.tachiyomi.animeextension.fr.modern",
                "17.0",
                modernSource);
        CatalogueSource catalogue = (CatalogueSource) adapted.source();
        SourcePage page = catalogue.latest(new SourceBrowseRequest(1, 20, List.of(), Map.of()));
        var filters = catalogue.filters();
        catalogue.search(new SourceSearchRequest(
                "example",
                new SourceBrowseRequest(
                        1,
                        20,
                        List.of(
                                new SourceFilterValue("filter.0", "wanted"),
                                new SourceFilterValue("filter.1", "true"),
                                new SourceFilterValue("filter.2", "Oldest"),
                                new SourceFilterValue("filter.3.0", "include")),
                        Map.of())));
        StreamingSource streaming = (StreamingSource) adapted.source();
        List<SourceEpisode> episodes = streaming.episodes(page.items().getFirst().id());
        var streams = streaming.streams(episodes.getFirst().id());
        counter.check(page.items().size() == 1
                        && adapted.source().descriptor().languageTag().equals("fr")
                        && episodes.size() == 1,
                "an ext-lib 17 suspend source must adapt catalogue and combined episode updates");
        counter.check(streams.size() == 1
                        && streams.getFirst().format().name().equals("HLS")
                        && streams.getFirst().subtitles().size() == 1,
                "an ext-lib 17 source must resolve hosters into Anilib video streams");
        counter.check(filters.size() == 5
                        && filters.get(0).type() == SourceFilterType.TEXT
                        && filters.get(3).type() == SourceFilterType.HEADER
                        && filters.get(4).groupId().equals("filter.3")
                        && modernSource.filterApplied(),
                "Aniyomi text, checkbox, select, group, and tri-state filters must round-trip");
    }

    private static void adaptsConfigurableAnimeSourcePreferences(Counter counter) {
        AtomicReference<Map<String, String>> applied = new AtomicReference<>(Map.of());
        AniyomiSourcePreferences preferences = new AniyomiSourcePreferences(
                List.of(
                        new SourcePreferenceDefinition(
                                "use_alt",
                                "Use alternate host",
                                "",
                                SourcePreferenceType.SWITCH,
                                List.of(),
                                "false",
                                false),
                        new SourcePreferenceDefinition(
                                "quality",
                                "Preferred quality",
                                "",
                                SourcePreferenceType.SELECT,
                                List.of("720p", "1080p"),
                                "1080p",
                                false)),
                applied::set);
        CatalogueSource catalogue = (CatalogueSource) AniyomiAnimeSourceAdapter.adapt(
                "eu.kanade.tachiyomi.animeextension.en.configurable",
                "17.0",
                new AniyomiAdapterFixture.Source(),
                () -> true,
                preferences).source();
        Map<String, String> selected = Map.of("use_alt", "true", "quality", "720p");
        catalogue.popular(new SourceBrowseRequest(1, 20, List.of(), selected));
        counter.check(catalogue.preferences().equals(preferences.definitions()),
                "configurable APK sources must expose their preference schema through the shared Source API");
        counter.check(applied.get().equals(selected),
                "shared Android and desktop preference selections must reach the APK source before requests");
    }

    private static void adaptsModernMangaSource(Counter counter) {
        AniyomiMangaSourceAdapter.AdaptedSource adapted = AniyomiMangaSourceAdapter.adapt(
                "eu.kanade.tachiyomi.extension.en.example",
                "1.6.1",
                new AniyomiAdapterFixture.ModernMangaSource());
        CatalogueSource catalogue = (CatalogueSource) adapted.source();
        SourcePage page = catalogue.popular(new SourceBrowseRequest(1, 20, List.of(), Map.of()));
        counter.check(page.items().size() == 1
                        && page.items().getFirst().title().equals("Example Manga")
                        && page.items().getFirst().contentKind() == SourceContentKind.MANGA,
                "an ABI-ready manga APK source must adapt its catalogue into Anilib models");
        PagedSource paged = (PagedSource) adapted.source();
        var chapters = paged.contentUnits(page.items().getFirst().id());
        counter.check(chapters.size() == 1
                        && chapters.getFirst().title().equals("Chapter 1")
                        && chapters.getFirst().publishedAt().isPresent(),
                "the manga APK bridge must project chapter identity and publication time");
        List<SourcePageResource> pages = paged.pages(chapters.getFirst().id());
        counter.check(pages.size() == 1
                        && pages.getFirst().index() == 0
                        && pages.getFirst().value().contains("page-1.jpg"),
                "the manga APK bridge must project a validated reader page sequence");
    }

    private static ExtensionPackageMetadata portablePackage(
            String packageName,
            byte[] bundle,
            KeyPair keyPair,
            long versionCode) {
        String checksum = sha256(bundle);
        String signature = signature(bundle, keyPair);
        return new ExtensionPackageMetadata(
                "Example",
                packageName,
                "en",
                versionCode,
                "1." + versionCode,
                false,
                ExtensionContentKind.MIXED,
                List.of(new ExtensionSourceMetadata("Example", "en", "42", Optional.of(BUNDLE))),
                List.of(new ExtensionArtifactMetadata(
                        ExtensionArtifactFormat.ANILIB_BUNDLE,
                        BUNDLE,
                        Optional.of(checksum),
                        Optional.of(signature),
                        Optional.of("example-publisher"),
                        Optional.of("1.4"))));
    }

    private static byte[] bundle(String packageName, long versionCode, String api) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (ZipOutputStream archive = new ZipOutputStream(bytes)) {
                archive.putNextEntry(new ZipEntry("META-INF/anilib-extension.properties"));
                archive.write(("package=" + packageName
                        + "\nversionCode=" + versionCode
                        + "\napi=" + api
                        + "\nmodule=fr.vriege.anilib.fixture"
                        + "\nsource.count=1"
                        + "\nsource.0.id=example.source"
                        + "\nsource.0.component=extension.example.source"
                        + "\nsource.0.name=Example"
                        + "\nsource.0.factory=fr.vriege.anilib.fixture.ExampleFactory\n")
                        .getBytes(StandardCharsets.UTF_8));
                archive.closeEntry();
            }
            return bytes.toByteArray();
        } catch (IOException exception) {
            throw new AssertionError("Unable to create portable Bundle fixture", exception);
        }
    }

    private static KeyPair keyPair() {
        try {
            return KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        } catch (GeneralSecurityException exception) {
            throw new AssertionError("JDK must provide Ed25519", exception);
        }
    }

    private static String signature(byte[] bytes, KeyPair keyPair) {
        try {
            Signature signature = Signature.getInstance("Ed25519");
            signature.initSign(keyPair.getPrivate());
            signature.update(bytes);
            return Base64.getEncoder().encodeToString(signature.sign());
        } catch (GeneralSecurityException exception) {
            throw new AssertionError("Unable to sign portable Bundle fixture", exception);
        }
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (GeneralSecurityException exception) {
            throw new AssertionError("JDK must provide SHA-256", exception);
        }
    }

    private static Path temporaryDirectory() {
        try {
            return Files.createTempDirectory("anilib-extension-repository-test");
        } catch (IOException exception) {
            throw new AssertionError("Unable to create extension repository test directory", exception);
        }
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
            throw new AssertionError("Unable to clean extension repository test directory", exception);
        }
    }

    private static final class RecordingClient implements AnilibHttpClient {
        private final byte[] body;
        private HttpRequest lastRequest;

        private RecordingClient(String body) {
            this.body = body.getBytes(StandardCharsets.UTF_8);
        }

        private RecordingClient(byte[] body) {
            this.body = body.clone();
        }

        @Override
        public HttpResponse execute(HttpRequest request) {
            lastRequest = request;
            return new HttpResponse(200, Map.of("content-type", List.of("application/json")), body, false);
        }
    }

    private static final class MiwayomiBridgeClient implements AnilibHttpClient {
        private boolean savedRepositories;
        private boolean installRequested;

        @Override
        public HttpResponse execute(HttpRequest request) {
            String path = request.uri().getPath();
            String body = switch (path) {
                case "/api/v1/health" -> """
                        {"status":"ok","service":"miwayomi","mangaSources":1,"animeSources":1}
                        """;
                case "/api/v1/sources" -> """
                        {"manga":[{"id":"42","name":"Manga APK","lang":"en","type":"manga","pkg":"manga.pkg"}],
                        "anime":[{"id":"43","name":"Anime APK","lang":"fr","type":"anime","pkg":"anime.pkg"}]}
                        """;
                case "/api/v1/manga/42/popular" -> """
                        {"hasNextPage":false,"mangas":[{"url":"/manga/bridge","title":"Bridge Manga",
                        "description":"Description","thumbnail_url":"https://cdn.example/manga.jpg"}]}
                        """;
                case "/api/v1/manga/42/chapters" -> """
                        {"chapters":[{"url":"/chapter/1","name":"Chapter 1","date_upload":1700000000000}]}
                        """;
                case "/api/v1/manga/42/pages" -> """
                        {"pages":[{"index":0,"number":1,"url":"","imageUrl":"https://cdn.example/page.jpg"}]}
                        """;
                case "/api/v1/anime/43/search" -> """
                        {"hasNextPage":false,"animes":[{"url":"/anime/bridge","title":"Bridge Anime",
                        "description":"Description","thumbnail_url":"https://cdn.example/anime.jpg"}]}
                        """;
                case "/api/v1/anime/43/episodes" -> """
                        {"episodes":[{"url":"/episode/1","name":"Episode 1","episode_number":1,
                        "date_upload":1700000000000,"scanlator":"Team",
                        "preview_url":"https://cdn.example/episode.jpg"}]}
                        """;
                case "/api/v1/anime/43/videos" -> """
                        {"videos":[{"videoUrl":"https://cdn.example/master.m3u8","videoTitle":"1080p",
                        "headers":{"Referer":"https://source.example/"},
                        "subtitleTracks":[{"url":"https://cdn.example/sub.vtt","lang":"fr"}]}]}
                        """;
                case "/api/v1/extensions/repos" -> {
                    savedRepositories = request.method().name().equals("POST")
                            && new String(request.body(), StandardCharsets.UTF_8).contains(INDEX.toString());
                    yield "{\"ok\":true}";
                }
                case "/api/v1/extensions/install" -> {
                    installRequested = request.method().name().equals("POST")
                            && new String(request.body(), StandardCharsets.UTF_8).contains("example.apk");
                    yield "{\"ok\":true,\"name\":\"Example APK\",\"pkg\":\"example.pkg\"}";
                }
                case "/api/v1/proxy" -> null;
                default -> throw new AssertionError("Unexpected desktop engine route: " + request.uri());
            };
            byte[] bytes = body == null ? "image".getBytes(StandardCharsets.UTF_8)
                    : body.getBytes(StandardCharsets.UTF_8);
            return new HttpResponse(200, Map.of("content-type", List.of("application/json")), bytes, false);
        }
    }

    private static final class GitHubIndexClient implements AnilibHttpClient {
        private final byte[] index;
        private final List<URI> requests = new java.util.ArrayList<>();

        private GitHubIndexClient(String index) {
            this.index = index.getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public HttpResponse execute(HttpRequest request) {
            requests.add(request.uri());
            if (!request.uri().getPath().equals("/example/anilib-sources/repo/index.json")) {
                return new HttpResponse(404, Map.of(), new byte[0], false);
            }
            return new HttpResponse(200, Map.of("content-type", List.of("application/json")), index, false);
        }
    }

    private static final class MutableRepositoryService implements ExtensionRepositoryService {
        private final List<ExtensionPackageMetadata> packages;

        private MutableRepositoryService(ExtensionPackageMetadata extensionPackage) {
            packages = List.of(extensionPackage);
        }

        @Override
        public List<URI> repositories() {
            return List.of(INDEX);
        }

        @Override
        public void add(URI indexUri) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean remove(URI indexUri) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<ExtensionRepositorySnapshot> snapshots() {
            return List.of();
        }

        @Override
        public ExtensionRepositorySnapshot refresh(URI indexUri) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<ExtensionRepositorySnapshot> refreshAll() {
            return List.of();
        }

        @Override
        public List<ExtensionPackageMetadata> packages() {
            return packages;
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

        private void expectIllegalArgument(Runnable action, String message) {
            try {
                action.run();
                throw new AssertionError(message);
            } catch (IllegalArgumentException expected) {
                value++;
            }
        }

        private void expectSecurity(Runnable action, String message) {
            try {
                action.run();
                throw new AssertionError(message);
            } catch (SecurityException expected) {
                value++;
            }
        }
    }
}
