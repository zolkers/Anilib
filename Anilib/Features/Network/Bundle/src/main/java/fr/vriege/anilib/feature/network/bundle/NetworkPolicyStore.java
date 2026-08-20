package fr.vriege.anilib.feature.network.bundle;

import fr.vriege.anilib.feature.network.NetworkPolicy;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import java.util.Properties;
import java.time.Duration;

final class NetworkPolicyStore {
    private final Path file;

    NetworkPolicyStore(Path file) {
        this.file = file.toAbsolutePath().normalize();
    }

    NetworkPolicy load() {
        NetworkPolicy defaults = NetworkPolicy.defaults();
        if (!Files.exists(file)) {
            return defaults;
        }
        if (!Files.isRegularFile(file) || Files.isSymbolicLink(file)) {
            throw new IllegalStateException("Network policy path must be a regular file");
        }
        Properties values = new Properties();
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            values.load(reader);
            return new NetworkPolicy(
                    values.getProperty("user-agent", defaults.userAgent()),
                    uri(values.getProperty("proxy")),
                    uri(values.getProperty("dns-over-https")),
                    Duration.ofSeconds(number(values, "timeout-seconds", 30)),
                    flag(values, "response-cache", true));
        } catch (IOException | IllegalArgumentException exception) {
            throw new IllegalStateException("Unable to load the network policy", exception);
        }
    }

    void save(NetworkPolicy policy) {
        Properties values = new Properties();
        values.setProperty("user-agent", policy.userAgent());
        values.setProperty("proxy", policy.proxy().map(URI::toASCIIString).orElse(""));
        values.setProperty("dns-over-https", policy.dnsOverHttps().map(URI::toASCIIString).orElse(""));
        values.setProperty("timeout-seconds", Long.toString(policy.timeout().toSeconds()));
        values.setProperty("response-cache", Boolean.toString(policy.responseCacheEnabled()));
        Path parent = file.getParent();
        Path temporary = null;
        try {
            Files.createDirectories(parent);
            temporary = Files.createTempFile(parent, ".network-", ".tmp");
            try (BufferedWriter writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
                values.store(writer, "Anilib network policy");
            }
            try {
                Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                throw new IOException("Atomic network policy replacement is unavailable", exception);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to store the network policy", exception);
        } finally {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException ignored) {
                    // The primary operation reports the actionable failure.
                }
            }
        }
    }

    private static Optional<URI> uri(String value) {
        return value == null || value.isBlank() ? Optional.empty() : Optional.of(URI.create(value.strip()));
    }

    private static int number(Properties values, String key, int fallback) {
        try {
            return Integer.parseInt(values.getProperty(key, Integer.toString(fallback)));
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private static boolean flag(Properties values, String key, boolean fallback) {
        String value = values.getProperty(key);
        return value == null ? fallback : Boolean.parseBoolean(value);
    }
}
