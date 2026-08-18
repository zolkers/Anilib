package fr.vriege.anilib.feature.applicationupdate;

import java.net.URI;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

public record ApplicationArtifact(
        ApplicationPlatform platform,
        String fileName,
        URI download,
        long sizeBytes,
        String sha256) {
    private static final Pattern SHA_256 = Pattern.compile("[0-9a-f]{64}");

    public ApplicationArtifact {
        Objects.requireNonNull(platform, "platform must not be null");
        fileName = Objects.requireNonNull(fileName, "fileName must not be null").strip();
        if (fileName.isEmpty() || fileName.contains("/") || fileName.contains("\\")) {
            throw new IllegalArgumentException("fileName must be a simple non-blank name");
        }
        download = Objects.requireNonNull(download, "download must not be null").normalize();
        if (!"https".equalsIgnoreCase(download.getScheme()) || download.getHost() == null) {
            throw new IllegalArgumentException("download must be an absolute HTTPS URI");
        }
        if (sizeBytes < 1 || sizeBytes > 1024L * 1024L * 1024L) {
            throw new IllegalArgumentException("sizeBytes must be between 1 byte and 1 GiB");
        }
        sha256 = Objects.requireNonNull(sha256, "sha256 must not be null")
                .strip()
                .toLowerCase(Locale.ROOT);
        if (!SHA_256.matcher(sha256).matches()) {
            throw new IllegalArgumentException("sha256 must contain 64 lowercase hexadecimal characters");
        }
    }
}
