package fr.vriege.anilib.feature.applicationupdate.runtime;

import fr.vriege.anilib.feature.applicationupdate.ApplicationPlatform;
import fr.vriege.anilib.feature.applicationupdate.ApplicationRelease;
import fr.vriege.anilib.feature.applicationupdate.ApplicationUpdateService;
import fr.vriege.anilib.feature.applicationupdate.ApplicationUpdateSnapshot;
import fr.vriege.anilib.feature.applicationupdate.ApplicationVersion;
import fr.vriege.anilib.framework.http.AnilibHttpClient;
import fr.vriege.anilib.framework.http.HttpRequest;
import fr.vriege.anilib.framework.http.HttpResponse;

import java.net.URI;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class GitHubApplicationUpdateService implements ApplicationUpdateService {
    private static final int MAX_RESPONSE_BYTES = 1024 * 1024;
    private static final Pattern TAG = Pattern.compile("\\\"tag_name\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");
    private static final Pattern PAGE = Pattern.compile("\\\"html_url\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");
    private final AnilibHttpClient httpClient;
    private final URI endpoint;
    private ApplicationUpdateSnapshot snapshot;

    public GitHubApplicationUpdateService(
            AnilibHttpClient httpClient,
            ApplicationVersion currentVersion,
            ApplicationPlatform platform,
            URI endpoint) {
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient must not be null");
        this.endpoint = Objects.requireNonNull(endpoint, "endpoint must not be null").normalize();
        if (!"https".equalsIgnoreCase(this.endpoint.getScheme()) || this.endpoint.getHost() == null) {
            throw new IllegalArgumentException("endpoint must be an absolute HTTPS URI");
        }
        snapshot = new ApplicationUpdateSnapshot(
                Objects.requireNonNull(currentVersion, "currentVersion must not be null"),
                Objects.requireNonNull(platform, "platform must not be null"),
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
            HttpResponse response = httpClient.execute(HttpRequest.builder(endpoint)
                    .header("accept", "application/vnd.github+json")
                    .header("user-agent", "Anilib-Application-Update")
                    .build());
            if (response.statusCode() != 200) {
                throw new IllegalStateException("Release service returned HTTP " + response.statusCode());
            }
            if (response.body().length > MAX_RESPONSE_BYTES) {
                throw new IllegalStateException("Release response exceeds 1 MiB");
            }
            ApplicationRelease release = parseRelease(response.bodyAsUtf8());
            Optional<ApplicationRelease> available = release.version().compareTo(snapshot.currentVersion()) > 0
                    ? Optional.of(release)
                    : Optional.empty();
            snapshot = new ApplicationUpdateSnapshot(
                    snapshot.currentVersion(),
                    snapshot.platform(),
                    available,
                    Optional.of(checkedAt),
                    Optional.empty());
        } catch (RuntimeException exception) {
            snapshot = new ApplicationUpdateSnapshot(
                    snapshot.currentVersion(),
                    snapshot.platform(),
                    Optional.empty(),
                    Optional.of(checkedAt),
                    Optional.ofNullable(exception.getMessage()).or(() -> Optional.of("Update check failed")));
        }
        return snapshot;
    }

    static ApplicationRelease parseRelease(String json) {
        String content = Objects.requireNonNull(json, "json must not be null");
        Matcher tag = TAG.matcher(content);
        Matcher page = PAGE.matcher(content);
        if (!tag.find() || !page.find()) {
            throw new IllegalArgumentException("Release response is missing tag_name or html_url");
        }
        return new ApplicationRelease(ApplicationVersion.parse(tag.group(1)), URI.create(page.group(1)));
    }
}
