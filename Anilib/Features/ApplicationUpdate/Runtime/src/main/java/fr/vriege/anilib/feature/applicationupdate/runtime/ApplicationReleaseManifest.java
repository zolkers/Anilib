package fr.vriege.anilib.feature.applicationupdate.runtime;

import fr.vriege.anilib.feature.applicationupdate.ApplicationArtifact;
import fr.vriege.anilib.feature.applicationupdate.ApplicationPlatform;
import fr.vriege.anilib.feature.applicationupdate.ApplicationRelease;
import fr.vriege.anilib.feature.applicationupdate.ApplicationUpdateChannel;
import fr.vriege.anilib.feature.applicationupdate.ApplicationVersion;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

final class ApplicationReleaseManifest {
    private static final int MAX_MANIFEST_BYTES = 64 * 1024;
    private static final String FORMAT = "anilib-update-v1";
    private static final String REPOSITORY = "zolkers/Anilib";
    private static final String WORKFLOW = ".github/workflows/application-release.yml";

    private ApplicationReleaseManifest() {
    }

    static ApplicationRelease verify(
            byte[] manifest,
            String encodedSignature,
            String encodedPublicKey,
            ApplicationPlatform platform,
            String changelog) {
        if (manifest.length == 0 || manifest.length > MAX_MANIFEST_BYTES) {
            throw new IllegalArgumentException("Release manifest must contain at most 64 KiB");
        }
        verifySignature(manifest, encodedSignature, encodedPublicKey);
        Map<String, String> values = parse(manifest);
        require(values, "format", FORMAT);
        require(values, "repository", REPOSITORY);
        require(values, "workflow", WORKFLOW);
        ApplicationVersion version = ApplicationVersion.parse(value(values, "version"));
        ApplicationUpdateChannel channel = ApplicationUpdateChannel.valueOf(
                value(values, "channel").toUpperCase(Locale.ROOT));
        URI releasePage = URI.create(value(values, "release"));
        URI licensePage = URI.create(value(values, "license"));
        String commit = value(values, "commit");
        Optional<ApplicationArtifact> artifact = platform == ApplicationPlatform.UNKNOWN
                ? Optional.empty()
                : Optional.of(artifact(values, platform));
        return new ApplicationRelease(
                version,
                releasePage,
                channel,
                changelog,
                licensePage,
                commit,
                artifact);
    }

    private static ApplicationArtifact artifact(Map<String, String> values, ApplicationPlatform platform) {
        String text = value(values, "artifact." + platform.name().toLowerCase(Locale.ROOT));
        String[] fields = text.split("\\|", -1);
        if (fields.length != 4) {
            throw new IllegalArgumentException("Release artifact entry must contain four fields");
        }
        try {
            return new ApplicationArtifact(
                    platform,
                    fields[0],
                    URI.create(fields[3]),
                    Long.parseLong(fields[1]),
                    fields[2]);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Release artifact size is invalid", exception);
        }
    }

    private static Map<String, String> parse(byte[] bytes) {
        String text = new String(bytes, StandardCharsets.UTF_8);
        if (text.indexOf('\r') >= 0) {
            throw new IllegalArgumentException("Release manifest must use LF line endings");
        }
        Map<String, String> values = new LinkedHashMap<>();
        for (String line : text.split("\\n", -1)) {
            if (line.isEmpty()) {
                continue;
            }
            int separator = line.indexOf('=');
            if (separator < 1 || separator == line.length() - 1) {
                throw new IllegalArgumentException("Release manifest contains an invalid line");
            }
            String name = line.substring(0, separator);
            String value = line.substring(separator + 1);
            if (!name.matches("[a-z.]+") || values.putIfAbsent(name, value) != null) {
                throw new IllegalArgumentException("Release manifest contains an invalid or duplicate field");
            }
        }
        return Map.copyOf(values);
    }

    private static void verifySignature(byte[] manifest, String signature, String publicKey) {
        try {
            byte[] publicBytes = Base64.getDecoder().decode(publicKey.strip());
            PublicKey key = KeyFactory.getInstance("Ed25519")
                    .generatePublic(new X509EncodedKeySpec(publicBytes));
            Signature verifier = Signature.getInstance("Ed25519");
            verifier.initVerify(key);
            verifier.update(manifest);
            if (!verifier.verify(Base64.getDecoder().decode(signature.strip()))) {
                throw new IllegalArgumentException("Release manifest signature is invalid");
            }
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("JDK 21 does not provide Ed25519 verification", exception);
        }
    }

    private static String value(Map<String, String> values, String name) {
        String value = values.get(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Release manifest is missing " + name);
        }
        return value;
    }

    private static void require(Map<String, String> values, String name, String expected) {
        if (!value(values, name).equals(expected)) {
            throw new IllegalArgumentException("Release manifest has an unexpected " + name);
        }
    }
}
