package fr.vriege.anilib.tooling.archtests;

import fr.vriege.anilib.feature.applicationupdate.ApplicationPlatform;
import fr.vriege.anilib.feature.applicationupdate.ApplicationUpdateChannel;
import fr.vriege.anilib.feature.applicationupdate.ApplicationUpdateSnapshot;
import fr.vriege.anilib.feature.applicationupdate.ApplicationVersion;
import fr.vriege.anilib.feature.applicationupdate.runtime.FileApplicationUpdateChannelStore;
import fr.vriege.anilib.feature.applicationupdate.runtime.GitHubApplicationUpdateService;
import fr.vriege.anilib.framework.http.HttpResponse;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.Signature;
import java.util.Base64;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;

final class ApplicationUpdateTest {
    private static final URI ENDPOINT =
            URI.create("https://api.github.com/repos/zolkers/Anilib/releases/latest");
    private static final URI MANIFEST = URI.create("https://downloads.example.test/anilib-update.manifest");
    private static final URI SIGNATURE = URI.create("https://downloads.example.test/anilib-update.manifest.sig");
    private static final String COMMIT = "0123456789abcdef0123456789abcdef01234567";

    private ApplicationUpdateTest() {
    }

    static int run() {
        Counter counter = new Counter();
        counter.check(ApplicationVersion.parse("v1.10.0").compareTo(ApplicationVersion.parse("1.9.9")) > 0,
                "semantic version comparison must compare each numeric component");
        counter.check(ApplicationVersion.parse("1.2.0-beta.10")
                        .compareTo(ApplicationVersion.parse("1.2.0-beta.2")) > 0
                        && ApplicationVersion.parse("1.2.0")
                        .compareTo(ApplicationVersion.parse("1.2.0-beta.10")) > 0,
                "semantic version comparison must order beta identifiers below stable releases");
        counter.check(ApplicationVersion.parse("1.2.0-beta.999999999999999999999")
                        .compareTo(ApplicationVersion.parse("1.2.0-beta.10")) > 0,
                "semantic version comparison must not overflow on numeric prerelease identifiers");

        Path directory = null;
        try {
            directory = Files.createTempDirectory("anilib-application-update");
            byte[] installer = "signed installer".getBytes(StandardCharsets.UTF_8);
            KeyPair keys = keyPair();
            byte[] manifest = manifest(installer, "v1.2.0", "stable");
            GitHubApplicationUpdateService update = service(
                    keys,
                    manifest,
                    sign(keys, manifest),
                    release("v1.2.0"),
                    null);
            ApplicationUpdateSnapshot available = update.checkNow();
            counter.check(available.error().isEmpty(), "a signed release response must not report an error");
            counter.check(available.availableRelease().orElseThrow().artifact().orElseThrow().sha256()
                            .equals(sha256(installer)),
                    "a newer signed release must expose its platform checksum");
            counter.check(available.availableRelease().orElseThrow().changelog().equals("Changes"),
                    "the GitHub changelog must be presented beside signed artifact metadata");

            Path downloaded = directory.resolve("Anilib-test.msi");
            Files.write(downloaded, installer);
            counter.check(update.verifyDownloadedArtifact(downloaded).sourceCommit().equals(COMMIT),
                    "download verification must bind size, SHA-256, and signed source commit");
            Files.writeString(downloaded, "tampered", StandardCharsets.UTF_8);
            counter.expectFailure(() -> update.verifyDownloadedArtifact(downloaded),
                    "tampered application installers must be rejected");

            byte[] invalidSignature = sign(keys, "other".getBytes(StandardCharsets.UTF_8));
            ApplicationUpdateSnapshot malformed = service(
                    keys,
                    manifest,
                    invalidSignature,
                    release("v1.2.0"),
                    null).checkNow();
            counter.check(malformed.availableRelease().isEmpty() && malformed.error().isPresent(),
                    "an invalid release signature must retain a user-visible error");

            Path channelFile = directory.resolve("channel.txt");
            FileApplicationUpdateChannelStore store = new FileApplicationUpdateChannelStore(channelFile);
            GitHubApplicationUpdateService channelService = service(
                    keys,
                    manifest,
                    sign(keys, manifest),
                    release("v1.2.0"),
                    store);
            channelService.setChannel(ApplicationUpdateChannel.BETA);
            GitHubApplicationUpdateService reopened = service(
                    keys,
                    manifest,
                    sign(keys, manifest),
                    release("v1.2.0"),
                    new FileApplicationUpdateChannelStore(channelFile));
            counter.check(reopened.snapshot().channel() == ApplicationUpdateChannel.BETA,
                    "stable or beta channel selection must survive a complete restart");
        } catch (IOException exception) {
            throw new AssertionError("Unable to test application updates", exception);
        } finally {
            if (directory != null) {
                delete(directory);
            }
        }
        return counter.value;
    }

    private static GitHubApplicationUpdateService service(
            KeyPair keys,
            byte[] manifest,
            byte[] signature,
            String release,
            FileApplicationUpdateChannelStore store) {
        Map<URI, byte[]> responses = new HashMap<>();
        responses.put(ENDPOINT, release.getBytes(StandardCharsets.UTF_8));
        responses.put(ENDPOINT.resolve("../releases?per_page=20"), release.getBytes(StandardCharsets.UTF_8));
        responses.put(MANIFEST, manifest);
        responses.put(SIGNATURE, Base64.getEncoder().encode(signature));
        return new GitHubApplicationUpdateService(
                request -> new HttpResponse(200, Map.of(), responses.get(request.uri()), false),
                ApplicationVersion.parse("1.0.0"),
                ApplicationPlatform.WINDOWS,
                ENDPOINT,
                Base64.getEncoder().encodeToString(keys.getPublic().getEncoded()),
                store);
    }

    private static String release(String version) {
        return """
                {
                  "tag_name":"%s",
                  "html_url":"https://github.com/zolkers/Anilib/releases/tag/%s",
                  "body":"Changes",
                  "assets":[
                    {"name":"anilib-update.manifest","browser_download_url":"%s"},
                    {"name":"anilib-update.manifest.sig","browser_download_url":"%s"}
                  ]
                }
                """.formatted(version, version, MANIFEST, SIGNATURE);
    }

    private static byte[] manifest(byte[] installer, String version, String channel) {
        return ("""
                format=anilib-update-v1
                repository=zolkers/Anilib
                workflow=.github/workflows/application-release.yml
                version=%s
                channel=%s
                commit=%s
                release=https://github.com/zolkers/Anilib/releases/tag/%s
                license=https://github.com/zolkers/Anilib/blob/%s/LICENSE
                artifact.windows=Anilib-test.msi|%d|%s|https://downloads.example.test/Anilib-test.msi
                """).formatted(
                version,
                channel,
                COMMIT,
                version,
                COMMIT,
                installer.length,
                sha256(installer)).getBytes(StandardCharsets.UTF_8);
    }

    private static KeyPair keyPair() {
        try {
            return KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        } catch (GeneralSecurityException exception) {
            throw new AssertionError("JDK must provide Ed25519", exception);
        }
    }

    private static byte[] sign(KeyPair keys, byte[] content) {
        try {
            Signature signer = Signature.getInstance("Ed25519");
            signer.initSign(keys.getPrivate());
            signer.update(content);
            return signer.sign();
        } catch (GeneralSecurityException exception) {
            throw new AssertionError("JDK must provide Ed25519", exception);
        }
    }

    private static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (GeneralSecurityException exception) {
            throw new AssertionError("JDK must provide SHA-256", exception);
        }
    }

    private static void delete(Path directory) {
        try (var paths = Files.walk(directory)) {
            for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        } catch (IOException exception) {
            throw new AssertionError("Unable to clean application update test", exception);
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

        private void expectFailure(Runnable action, String message) {
            try {
                action.run();
                throw new AssertionError(message);
            } catch (IllegalArgumentException expected) {
                value++;
            }
        }
    }
}
