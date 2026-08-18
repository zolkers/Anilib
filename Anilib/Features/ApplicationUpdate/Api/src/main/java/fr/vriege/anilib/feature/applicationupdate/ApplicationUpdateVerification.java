package fr.vriege.anilib.feature.applicationupdate;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Objects;

public record ApplicationUpdateVerification(
        Path artifact,
        String sha256,
        String sourceCommit,
        Instant verifiedAt) {
    public ApplicationUpdateVerification {
        artifact = Objects.requireNonNull(artifact, "artifact must not be null")
                .toAbsolutePath()
                .normalize();
        sha256 = Objects.requireNonNull(sha256, "sha256 must not be null");
        sourceCommit = Objects.requireNonNull(sourceCommit, "sourceCommit must not be null");
        Objects.requireNonNull(verifiedAt, "verifiedAt must not be null");
    }
}
