package fr.vriege.anilib.tooling.archtests;

import fr.vriege.anilib.feature.extensionrepository.ExtensionArtifactFormat;
import fr.vriege.anilib.feature.extensionrepository.ExtensionPackageMetadata;
import fr.vriege.anilib.feature.extensionrepository.ExtensionRepositorySnapshot;
import fr.vriege.anilib.feature.extensionrepository.runtime.AniyomiRepositoryIndexParser;
import fr.vriege.anilib.feature.extensionrepository.runtime.DefaultExtensionRepositoryService;
import fr.vriege.anilib.feature.extensionrepository.runtime.FileExtensionRepositoryStore;
import fr.vriege.anilib.framework.http.AnilibHttpClient;
import fr.vriege.anilib.framework.http.HttpRequest;
import fr.vriege.anilib.framework.http.HttpResponse;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

/** Behavior checks for user-supplied Aniyomi-compatible extension repositories. */
final class ExtensionRepositoryTest {
    private static final URI INDEX = URI.create("https://repo.example/extensions/index.min.json");
    private static final String SHA_256 = "0123456789abcdef0123456789abcdef"
            + "0123456789abcdef0123456789abcdef";

    private ExtensionRepositoryTest() {
    }

    static int run() {
        Counter counter = new Counter();
        parsesAniyomiAndPortableArtifacts(counter);
        rejectsUnsafeMetadata(counter);
        persistsAndRefreshesUserRepositories(counter);
        return counter.value;
    }

    private static void parsesAniyomiAndPortableArtifacts(Counter counter) {
        String index = """
                [{
                  "name":"Aniyomi: Example",
                  "pkg":"eu.kanade.tachiyomi.animeextension.en.example",
                  "apk":"example-v14.2.apk",
                  "lang":"en",
                  "code":2,
                  "version":"14.2",
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
        counter.check(extension.artifacts().size() == 2,
                "one compatible index entry must expose APK and portable Bundle artifacts");
        counter.check(extension.artifacts().getFirst().format() == ExtensionArtifactFormat.ANIYOMI_APK,
                "Aniyomi apk metadata must remain identifiable");
        counter.check(extension.artifacts().get(1).sha256().orElseThrow().equals(SHA_256),
                "portable Bundle metadata must retain its checksum");
        counter.check(extension.sources().getFirst().baseUri().isEmpty(),
                "Aniyomi indexes may advertise a source with an empty baseUrl");
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
                "repository entries must reject invalid package identities and empty sources");
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

        @Override
        public HttpResponse execute(HttpRequest request) {
            lastRequest = request;
            return new HttpResponse(200, Map.of("content-type", List.of("application/json")), body, false);
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
    }
}
