package fr.vriege.anilib.feature.extensionrepository;

import fr.vriege.anilib.foundation.validation.Preconditions;

import java.net.URI;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

public record ExtensionArtifactMetadata(
        ExtensionArtifactFormat format,
        URI uri,
        Optional<String> sha256,
        Optional<String> signature,
        Optional<String> signingKeyId,
        Optional<String> requiredApiVersion) {
    private static final Pattern SHA_256 = Pattern.compile("[0-9a-f]{64}");

    public ExtensionArtifactMetadata {
        format = Preconditions.requireNonNull(format, "format");
        uri = requireHttps(uri);
        sha256 = normalizedOptional(sha256, "sha256").map(value -> value.toLowerCase(Locale.ROOT));
        signature = normalizedOptional(signature, "signature");
        signingKeyId = normalizedOptional(signingKeyId, "signingKeyId");
        requiredApiVersion = normalizedOptional(requiredApiVersion, "requiredApiVersion");
        if (sha256.isPresent() && !SHA_256.matcher(sha256.orElseThrow()).matches()) {
            throw new IllegalArgumentException("sha256 must contain 64 hexadecimal characters");
        }
        if (signature.isPresent() != signingKeyId.isPresent()) {
            throw new IllegalArgumentException("signature and signingKeyId must be declared together");
        }
    }

    private static URI requireHttps(URI value) {
        URI normalized = Preconditions.requireNonNull(value, "uri").normalize();
        if (!"https".equalsIgnoreCase(normalized.getScheme())
                || normalized.getHost() == null
                || normalized.getHost().isBlank()
                || normalized.getUserInfo() != null
                || normalized.getFragment() != null) {
            throw new IllegalArgumentException("artifact URI must be an absolute HTTPS URI without credentials");
        }
        return normalized;
    }

    private static Optional<String> normalizedOptional(Optional<String> value, String name) {
        Optional<String> optional = Preconditions.requireNonNull(value, name);
        return optional.map(item -> Preconditions.requireNonBlank(item, name));
    }
}
