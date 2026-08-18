package fr.vriege.anilib.feature.applicationupdate;

import java.net.URI;
import java.util.Objects;
import java.util.Optional;

public record ApplicationRelease(
        ApplicationVersion version,
        URI releasePage,
        ApplicationUpdateChannel channel,
        String changelog,
        URI licensePage,
        String sourceCommit,
        Optional<ApplicationArtifact> artifact) {
    public ApplicationRelease {
        Objects.requireNonNull(version, "version must not be null");
        releasePage = Objects.requireNonNull(releasePage, "releasePage must not be null").normalize();
        if (!"https".equalsIgnoreCase(releasePage.getScheme()) || releasePage.getHost() == null) {
            throw new IllegalArgumentException("releasePage must be an absolute HTTPS URI");
        }
        channel = Objects.requireNonNull(channel, "channel must not be null");
        changelog = Objects.requireNonNull(changelog, "changelog must not be null").strip();
        licensePage = Objects.requireNonNull(licensePage, "licensePage must not be null").normalize();
        if (!"https".equalsIgnoreCase(licensePage.getScheme()) || licensePage.getHost() == null) {
            throw new IllegalArgumentException("licensePage must be an absolute HTTPS URI");
        }
        sourceCommit = Objects.requireNonNull(sourceCommit, "sourceCommit must not be null").strip();
        if (!sourceCommit.matches("[0-9a-f]{40}")) {
            throw new IllegalArgumentException("sourceCommit must contain a lowercase Git commit SHA");
        }
        artifact = Objects.requireNonNull(artifact, "artifact must not be null");
        artifact.ifPresent(value -> {
            if (value.platform() == ApplicationPlatform.UNKNOWN) {
                throw new IllegalArgumentException("artifact platform must be known");
            }
        });
    }

    public ApplicationRelease(ApplicationVersion version, URI releasePage) {
        this(
                version,
                releasePage,
                ApplicationUpdateChannel.STABLE,
                "",
                URI.create("https://github.com/zolkers/Anilib/blob/main/LICENSE"),
                "0000000000000000000000000000000000000000",
                Optional.empty());
    }
}
