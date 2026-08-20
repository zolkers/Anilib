package fr.vriege.anilib.feature.applicationupdate.runtime;

import fr.vriege.anilib.feature.applicationupdate.ApplicationArtifact;
import fr.vriege.anilib.feature.applicationupdate.ApplicationPlatform;
import fr.vriege.anilib.feature.applicationupdate.ApplicationRelease;
import fr.vriege.anilib.feature.applicationupdate.ApplicationUpdateChannel;
import fr.vriege.anilib.feature.applicationupdate.ApplicationUpdateService;
import fr.vriege.anilib.feature.applicationupdate.ApplicationUpdateSnapshot;
import fr.vriege.anilib.feature.applicationupdate.ApplicationUpdateVerification;
import fr.vriege.anilib.feature.applicationupdate.ApplicationVersion;
import fr.vriege.anilib.framework.http.AnilibHttpClient;
import fr.vriege.anilib.framework.http.HttpRequest;
import fr.vriege.anilib.framework.http.HttpResponse;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.nio.charset.StandardCharsets;

public final class GitHubApplicationUpdateService implements ApplicationUpdateService {
    private static final int MAX_RELEASE_BYTES = 1024 * 1024;
    private static final int MAX_MANIFEST_BYTES = 64 * 1024;
    private static final Pattern TAG = Pattern.compile("\\\"tag_name\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");
    private static final Pattern PAGE = Pattern.compile("\\\"html_url\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");
    private static final Pattern BODY = Pattern.compile("\\\"body\\\"\\s*:\\s*\\\"((?:\\\\.|[^\\\"])*)\\\"");
    private static final String MANIFEST_NAME = "anilib-update.manifest";
    private static final String SIGNATURE_NAME = "anilib-update.manifest.sig";
    private final AnilibHttpClient httpClient;
    private final URI stableEndpoint;
    private final URI betaEndpoint;
    private final String publicKey;
    private final FileApplicationUpdateChannelStore channelStore;
    private ApplicationUpdateSnapshot snapshot;

    public GitHubApplicationUpdateService(
            AnilibHttpClient httpClient,
            ApplicationVersion currentVersion,
            ApplicationPlatform platform,
            URI endpoint,
            String publicKey,
            FileApplicationUpdateChannelStore channelStore) {
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient must not be null");
        stableEndpoint = requireEndpoint(endpoint);
        betaEndpoint = stableEndpoint.resolve("../releases?per_page=20");
        this.publicKey = Objects.requireNonNull(publicKey, "publicKey must not be null").strip();
        if (this.publicKey.isEmpty()) {
            throw new IllegalArgumentException("publicKey must not be blank");
        }
        this.channelStore = channelStore;
        ApplicationUpdateChannel channel = channelStore == null
                ? ApplicationUpdateChannel.STABLE
                : channelStore.load();
        snapshot = new ApplicationUpdateSnapshot(
                Objects.requireNonNull(currentVersion, "currentVersion must not be null"),
                Objects.requireNonNull(platform, "platform must not be null"),
                channel,
                Optional.empty(),
                Optional.empty(),
                Optional.empty());
    }

    @Override
    public synchronized ApplicationUpdateSnapshot snapshot() {
        return snapshot;
    }

    @Override
    public synchronized ApplicationUpdateSnapshot checkNow() {
        Instant checkedAt = Instant.now();
        try {
            URI endpoint = snapshot.channel() == ApplicationUpdateChannel.STABLE
                    ? stableEndpoint
                    : betaEndpoint;
            String releaseJson = new String(
                    fetch(endpoint, MAX_RELEASE_BYTES),
                    StandardCharsets.UTF_8);
            String tag = match(TAG, releaseJson, "tag_name");
            URI page = URI.create(match(PAGE, releaseJson, "html_url"));
            String changelog = optionalMatch(BODY, releaseJson).map(GitHubApplicationUpdateService::unescape)
                    .orElse("");
            URI manifestUri = asset(releaseJson, MANIFEST_NAME);
            URI signatureUri = asset(releaseJson, SIGNATURE_NAME);
            byte[] manifest = fetch(manifestUri, MAX_MANIFEST_BYTES);
            String signature = new String(
                    fetch(signatureUri, 1024),
                    StandardCharsets.US_ASCII);
            ApplicationRelease release = ApplicationReleaseManifest.verify(
                    manifest,
                    signature,
                    publicKey,
                    snapshot.platform(),
                    changelog);
            if (!release.version().equals(ApplicationVersion.parse(tag)) || !release.releasePage().equals(page)) {
                throw new IllegalStateException("Signed manifest does not match the GitHub release");
            }
            if (snapshot.channel() == ApplicationUpdateChannel.STABLE
                    && release.channel() != ApplicationUpdateChannel.STABLE) {
                throw new IllegalStateException("Stable channel rejected a beta release");
            }
            Optional<ApplicationRelease> available = release.version().compareTo(snapshot.currentVersion()) > 0
                    ? Optional.of(release)
                    : Optional.empty();
            snapshot = new ApplicationUpdateSnapshot(
                    snapshot.currentVersion(),
                    snapshot.platform(),
                    snapshot.channel(),
                    available,
                    Optional.of(checkedAt),
                    Optional.empty());
        } catch (RuntimeException exception) {
            snapshot = new ApplicationUpdateSnapshot(
                    snapshot.currentVersion(),
                    snapshot.platform(),
                    snapshot.channel(),
                    Optional.empty(),
                    Optional.of(checkedAt),
                    Optional.ofNullable(exception.getMessage()).or(() -> Optional.of("Update check failed")));
        }
        return snapshot;
    }

    @Override
    public synchronized ApplicationUpdateSnapshot setChannel(ApplicationUpdateChannel channel) {
        ApplicationUpdateChannel selected = Objects.requireNonNull(channel, "channel must not be null");
        if (channelStore != null) {
            channelStore.save(selected);
        }
        snapshot = new ApplicationUpdateSnapshot(
                snapshot.currentVersion(),
                snapshot.platform(),
                selected,
                Optional.empty(),
                snapshot.lastCheckedAt(),
                Optional.empty());
        return snapshot;
    }

    @Override
    public synchronized ApplicationUpdateVerification verifyDownloadedArtifact(Path path) {
        ApplicationRelease release = snapshot.availableRelease()
                .orElseThrow(() -> new IllegalStateException("No application update is available"));
        ApplicationArtifact expected = release.artifact()
                .orElseThrow(() -> new IllegalStateException("No installer is published for this platform"));
        Path artifact = Objects.requireNonNull(path, "artifact must not be null").toAbsolutePath().normalize();
        if (!artifact.getFileName().toString().equals(expected.fileName())) {
            throw new IllegalArgumentException("Downloaded installer name does not match the signed manifest");
        }
        try {
            if (!Files.isRegularFile(artifact) || Files.isSymbolicLink(artifact)) {
                throw new IllegalArgumentException("Downloaded installer must be a regular file");
            }
            if (Files.size(artifact) != expected.sizeBytes()) {
                throw new IllegalArgumentException("Downloaded installer size does not match the signed manifest");
            }
            String digest = digest(artifact);
            if (!MessageDigest.isEqual(
                    digest.getBytes(StandardCharsets.US_ASCII),
                    expected.sha256().getBytes(StandardCharsets.US_ASCII))) {
                throw new IllegalArgumentException("Downloaded installer checksum does not match the signed manifest");
            }
            return new ApplicationUpdateVerification(artifact, digest, release.sourceCommit(), Instant.now());
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to verify the downloaded installer", exception);
        }
    }

    private byte[] fetch(URI uri, int maximumBytes) {
        HttpResponse response = httpClient.execute(HttpRequest.builder(uri)
                .header("accept", "application/vnd.github+json, application/octet-stream")
                .header("user-agent", "Anilib-Application-Update")
                .build());
        if (response.statusCode() != 200) {
            throw new IllegalStateException("Release service returned HTTP " + response.statusCode());
        }
        if (response.body().length > maximumBytes) {
            throw new IllegalStateException("Release response exceeds its size limit");
        }
        return response.body();
    }

    private static URI requireEndpoint(URI endpoint) {
        URI value = Objects.requireNonNull(endpoint, "endpoint must not be null").normalize();
        if (!"https".equalsIgnoreCase(value.getScheme()) || value.getHost() == null) {
            throw new IllegalArgumentException("endpoint must be an absolute HTTPS URI");
        }
        return value;
    }

    private static URI asset(String json, String name) {
        Pattern pattern = Pattern.compile(
                "\\\"name\\\"\\s*:\\s*\\\"" + Pattern.quote(name)
                        + "\\\"[\\s\\S]{0,8192}?\\\"browser_download_url\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");
        return URI.create(match(pattern, json, "asset " + name));
    }

    private static String match(Pattern pattern, String input, String name) {
        return optionalMatch(pattern, input)
                .orElseThrow(() -> new IllegalArgumentException("Release response is missing " + name));
    }

    private static Optional<String> optionalMatch(Pattern pattern, String input) {
        Matcher matcher = pattern.matcher(input);
        return matcher.find() ? Optional.of(matcher.group(1)) : Optional.empty();
    }

    private static String unescape(String value) {
        return value.replace("\\r", "")
                .replace("\\n", "\n")
                .replace("\\t", "\t")
                .replace("\\\"", "\"")
                .replace("\\\\", "\\");
    }

    private static String digest(Path artifact) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = Files.newInputStream(artifact)) {
                byte[] buffer = new byte[64 * 1024];
                int count;
                while ((count = input.read(buffer)) >= 0) {
                    digest.update(buffer, 0, count);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JDK does not provide SHA-256", exception);
        }
    }
}
