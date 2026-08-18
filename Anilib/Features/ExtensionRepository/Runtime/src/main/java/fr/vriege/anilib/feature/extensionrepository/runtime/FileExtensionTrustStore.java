package fr.vriege.anilib.feature.extensionrepository.runtime;

import fr.vriege.anilib.foundation.validation.Preconditions;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** Atomic user-owned Ed25519 publisher trust store. */
public final class FileExtensionTrustStore {
    private static final Pattern KEY_ID = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,79}");
    private static final int MAX_KEYS = 1_000;
    private final Path file;

    public FileExtensionTrustStore(Path file) {
        this.file = Preconditions.requireNonNull(file, "file").toAbsolutePath().normalize();
    }

    public Map<String, PublicKey> load() {
        if (!Files.exists(file)) {
            return Map.of();
        }
        try {
            Map<String, PublicKey> keys = new LinkedHashMap<>();
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                if (line.isBlank()) {
                    continue;
                }
                int separator = line.indexOf('=');
                if (separator < 1) {
                    throw new IllegalStateException("Malformed extension trust-store entry");
                }
                String keyId = requireKeyId(line.substring(0, separator));
                PublicKey previous = keys.put(keyId, decode(line.substring(separator + 1)));
                if (previous != null || keys.size() > MAX_KEYS) {
                    throw new IllegalStateException("Invalid extension trust-store key set");
                }
            }
            return Map.copyOf(keys);
        } catch (IOException exception) {
            throw new UncheckedIOException("Unable to read extension trust store " + file, exception);
        }
    }

    public void save(Map<String, PublicKey> keys) {
        Map<String, PublicKey> values = Map.copyOf(Preconditions.requireNonNull(keys, "keys"));
        if (values.size() > MAX_KEYS) {
            throw new IllegalArgumentException("Extension trust store cannot exceed " + MAX_KEYS + " keys");
        }
        List<String> lines = new ArrayList<>();
        values.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            String encoded = Base64.getEncoder().encodeToString(entry.getValue().getEncoded());
            lines.add(requireKeyId(entry.getKey()) + "=" + encoded);
        });
        String content = String.join(System.lineSeparator(), lines)
                + (lines.isEmpty() ? "" : System.lineSeparator());
        writeAtomically(content);
    }

    public static String requireKeyId(String value) {
        String keyId = Preconditions.requireNonBlank(value, "keyId");
        if (!KEY_ID.matcher(keyId).matches()) {
            throw new IllegalArgumentException("keyId must use 1-80 letters, digits, dots, dashes, or underscores");
        }
        return keyId;
    }

    public static PublicKey decode(String encoded) {
        try {
            byte[] bytes = Base64.getDecoder().decode(Preconditions.requireNonBlank(encoded, "publicKey"));
            return KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(bytes));
        } catch (IllegalArgumentException | InvalidKeySpecException exception) {
            throw new IllegalArgumentException("publicKey must be a Base64 X.509 Ed25519 public key", exception);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JDK does not provide Ed25519", exception);
        }
    }

    private void writeAtomically(String content) {
        Path parent = file.getParent();
        Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        try {
            Files.createDirectories(parent);
            Files.writeString(temporary, content, StandardCharsets.UTF_8);
            try {
                Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            throw new UncheckedIOException("Unable to write extension trust store " + file, exception);
        }
    }
}
