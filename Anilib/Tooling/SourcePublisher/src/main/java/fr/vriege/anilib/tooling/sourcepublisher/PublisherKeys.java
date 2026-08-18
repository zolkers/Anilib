package fr.vriege.anilib.tooling.sourcepublisher;

import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

/** Ed25519 key generation and private-key loading. */
final class PublisherKeys {
    private PublisherKeys() {
    }

    static void generate(java.nio.file.Path privateFile, java.nio.file.Path publicFile) {
        if (java.nio.file.Files.exists(privateFile) || java.nio.file.Files.exists(publicFile)) {
            throw new IllegalArgumentException("Publisher key output already exists");
        }
        try {
            KeyPair pair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
            PublisherFiles.writeText(privateFile, encode(pair.getPrivate().getEncoded()));
            PublisherFiles.writeText(publicFile, encode(pair.getPublic().getEncoded()));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("JDK 21 does not provide Ed25519", exception);
        }
    }

    static PrivateKey privateKey(java.nio.file.Path path) {
        String encoded = new String(PublisherFiles.read(path, "private key"), java.nio.charset.StandardCharsets.UTF_8)
                .strip();
        try {
            byte[] bytes = Base64.getDecoder().decode(encoded);
            return KeyFactory.getInstance("Ed25519").generatePrivate(new PKCS8EncodedKeySpec(bytes));
        } catch (IllegalArgumentException | GeneralSecurityException exception) {
            throw new IllegalArgumentException("Private key must be Base64 PKCS#8 Ed25519", exception);
        }
    }

    private static String encode(byte[] bytes) {
        return Base64.getEncoder().encodeToString(bytes) + System.lineSeparator();
    }
}
