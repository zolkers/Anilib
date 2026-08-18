package fr.vriege.anilib.framework.http.runtime;

import fr.vriege.anilib.framework.http.HttpCacheEntry;
import fr.vriege.anilib.framework.http.HttpException;
import fr.vriege.anilib.framework.http.HttpResponse;
import fr.vriege.anilib.framework.http.HttpResponseCache;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

public final class FileHttpResponseCache implements HttpResponseCache {
    private static final int MAGIC = 0x41484331;
    private static final int MAX_BODY_BYTES = 16 * 1024 * 1024;
    private static final int MAX_HEADERS = 128;
    private static final int MAX_VALUES_PER_HEADER = 32;
    private static final HexFormat HEX = HexFormat.of();

    private final Path root;

    public FileHttpResponseCache(Path root) {
        this.root = Objects.requireNonNull(root, "root must not be null").toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.root);
            if (Files.isSymbolicLink(this.root) || !Files.isDirectory(this.root, LinkOption.NOFOLLOW_LINKS)) {
                throw new HttpException("HTTP cache root must be a real directory");
            }
        } catch (IOException exception) {
            throw failure("create the HTTP cache root", exception);
        }
    }

    @Override
    public synchronized Optional<HttpCacheEntry> find(String key) {
        Path file = fileFor(key);
        if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS)) {
            return Optional.empty();
        }
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
            throw new HttpException("HTTP cache entry must be a regular file");
        }
        try {
            HttpCacheEntry entry = read(file);
            if (!entry.isFresh(Instant.now())) {
                Files.deleteIfExists(file);
                return Optional.empty();
            }
            return Optional.of(entry);
        } catch (IOException | IllegalArgumentException exception) {
            deleteInvalid(file, exception);
            return Optional.empty();
        }
    }

    @Override
    public synchronized void store(String key, HttpCacheEntry entry) {
        Objects.requireNonNull(entry, "entry must not be null");
        Path destination = fileFor(key);
        Path temporary;
        try {
            temporary = Files.createTempFile(root, ".http-", ".tmp");
        } catch (IOException exception) {
            throw failure("create a temporary HTTP cache entry", exception);
        }
        try {
            write(temporary, entry);
            moveAtomically(temporary, destination);
        } catch (IOException exception) {
            throw failure("store an HTTP cache entry", exception);
        } finally {
            try {
                Files.deleteIfExists(temporary);
            } catch (IOException ignored) {
                // The primary cache operation reports the actionable failure.
            }
        }
    }

    @Override
    public synchronized void invalidate(String key) {
        delete(fileFor(key), "invalidate an HTTP cache entry");
    }

    @Override
    public synchronized void clear() {
        try (Stream<Path> entries = Files.list(root)) {
            for (Path entry : entries.toList()) {
                if (entry.getFileName().toString().endsWith(".http")
                        && Files.isRegularFile(entry, LinkOption.NOFOLLOW_LINKS)) {
                    Files.delete(entry);
                }
            }
        } catch (IOException exception) {
            throw failure("clear the HTTP response cache", exception);
        }
    }

    private Path fileFor(String key) {
        String value = Objects.requireNonNull(key, "key must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("key must not be blank");
        }
        return root.resolve(digest(value) + ".http");
    }

    private static HttpCacheEntry read(Path file) throws IOException {
        try (DataInputStream input = new DataInputStream(
                new BufferedInputStream(Files.newInputStream(file)))) {
            if (input.readInt() != MAGIC) {
                throw new IOException("Unsupported HTTP cache format");
            }
            Instant expiresAt = Instant.ofEpochMilli(input.readLong());
            int status = input.readInt();
            int headerCount = bounded(input.readInt(), MAX_HEADERS, "header count");
            Map<String, List<String>> headers = new LinkedHashMap<>();
            for (int headerIndex = 0; headerIndex < headerCount; headerIndex++) {
                String name = input.readUTF();
                int valueCount = bounded(input.readInt(), MAX_VALUES_PER_HEADER, "header value count");
                List<String> values = new ArrayList<>(valueCount);
                for (int valueIndex = 0; valueIndex < valueCount; valueIndex++) {
                    values.add(input.readUTF());
                }
                headers.put(name, List.copyOf(values));
            }
            int bodyLength = bounded(input.readInt(), MAX_BODY_BYTES, "body length");
            byte[] body = input.readNBytes(bodyLength);
            if (body.length != bodyLength || input.read() != -1) {
                throw new EOFException("HTTP cache entry has an invalid body length");
            }
            return new HttpCacheEntry(new HttpResponse(status, headers, body, false), expiresAt);
        }
    }

    private static void write(Path file, HttpCacheEntry entry) throws IOException {
        HttpResponse response = entry.response();
        byte[] body = response.body();
        bounded(body.length, MAX_BODY_BYTES, "body length");
        bounded(response.headers().size(), MAX_HEADERS, "header count");
        try (DataOutputStream output = new DataOutputStream(
                new BufferedOutputStream(Files.newOutputStream(file)))) {
            output.writeInt(MAGIC);
            output.writeLong(entry.expiresAt().toEpochMilli());
            output.writeInt(response.statusCode());
            output.writeInt(response.headers().size());
            for (Map.Entry<String, List<String>> header : response.headers().entrySet()) {
                bounded(header.getValue().size(), MAX_VALUES_PER_HEADER, "header value count");
                output.writeUTF(header.getKey());
                output.writeInt(header.getValue().size());
                for (String value : header.getValue()) {
                    output.writeUTF(value);
                }
            }
            output.writeInt(body.length);
            output.write(body);
        }
    }

    private static int bounded(int value, int maximum, String name) throws IOException {
        if (value < 0 || value > maximum) {
            throw new IOException("Invalid HTTP cache " + name);
        }
        return value;
    }

    private static String digest(String key) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HEX.formatHex(digest.digest(key.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JDK does not provide SHA-256", exception);
        }
    }

    private static void moveAtomically(Path source, Path destination) throws IOException {
        try {
            Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void deleteInvalid(Path file, Exception cause) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException exception) {
            cause.addSuppressed(exception);
            throw failure("discard an invalid HTTP cache entry", cause);
        }
    }

    private static void delete(Path file, String operation) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException exception) {
            throw failure(operation, exception);
        }
    }

    private static HttpException failure(String operation, Exception cause) {
        return new HttpException("Unable to " + operation, cause);
    }
}
