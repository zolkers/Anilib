package fr.vriege.anilib.platform.desktopextensionhost.compat.android.media;

import fr.vriege.anilib.framework.concurrent.runtime.ManagedExecutors;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/* Metadata adapter backed by Anilib's bundled FFprobe, with bounded input and process lifetime. */
public class MediaMetadataRetriever implements AutoCloseable {
    public static final int METADATA_KEY_DURATION = 9;
    public static final int METADATA_KEY_VIDEO_WIDTH = 18;
    public static final int METADATA_KEY_VIDEO_HEIGHT = 19;
    public static final int METADATA_KEY_VIDEO_ROTATION = 24;
    private String location;
    private Map<String, String> headers = Map.of();
    private Map<Integer, String> metadata;
    private boolean closed;

    public MediaMetadataRetriever() {}

    public synchronized void setDataSource(String path) {
        setDataSource(path, Map.of());
    }

    public synchronized void setDataSource(String path, Map<String, String> headers) {
        requireOpen();
        URI uri = URI.create(path);
        if (uri.getScheme() != null && !Set.of("http", "https", "file").contains(uri.getScheme())) {
            throw new IllegalArgumentException("Unsupported metadata URI scheme");
        }
        this.location = path;
        this.headers = headers == null ? Map.of() : Map.copyOf(headers);
        this.metadata = null;
    }

    public synchronized String extractMetadata(int key) {
        requireOpen();
        if (location == null) throw new IllegalStateException("Metadata data source is not set");
        if (metadata == null) metadata = probe();
        return metadata.get(key);
    }

    public synchronized void release() {
        closed = true;
        metadata = null;
        location = null;
    }

    @Override
    public void close() {
        release();
    }

    private Map<Integer, String> probe() {
        String configured = System.getProperty("anilib.ffprobe.path");
        String suffix =
                System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("windows")
                        ? ".exe"
                        : "";
        String resources = System.getProperty("compose.application.resources.dir");
        Path executable =
                configured != null
                        ? Path.of(configured)
                        : resources != null
                                ? Path.of(resources).resolve("ffmpeg/bin/ffprobe" + suffix)
                                : null;
        if (executable == null || !Files.isRegularFile(executable)) {
            throw new UnsupportedOperationException(
                    "FFprobe is required for extension media metadata");
        }
        ArrayList<String> command =
                new ArrayList<>(
                        List.of(executable.toString(), "-v", "error", "-rw_timeout", "10000000"));
        if (!headers.isEmpty()) {
            StringBuilder values = new StringBuilder();
            headers.forEach(
                    (name, value) -> {
                        if (name.contains("\r")
                                || name.contains("\n")
                                || value.contains("\r")
                                || value.contains("\n")) {
                            throw new IllegalArgumentException("Invalid metadata request header");
                        }
                        values.append(name).append(": ").append(value).append("\r\n");
                    });
            command.addAll(List.of("-headers", values.toString()));
        }
        command.addAll(
                List.of(
                        "-show_entries",
                        "format=duration:stream=width,height",
                        "-of",
                        "flat",
                        location));
        Process process = null;
        try {
            process =
                    new ProcessBuilder(command)
                            .redirectError(ProcessBuilder.Redirect.DISCARD)
                            .start();
            Process running = process;
            var output =
                    ManagedExecutors.supply(
                            "extension-media-metadata",
                            () -> {
                                try {
                                    return running.getInputStream().readNBytes(65537);
                                } catch (IOException exception) {
                                    throw new UncheckedIOException(exception);
                                }
                            });
            if (!process.waitFor(15, TimeUnit.SECONDS))
                throw new IllegalStateException("Media metadata request timed out");
            byte[] bytes = output.get(1, TimeUnit.SECONDS);
            if (process.exitValue() != 0 || bytes.length > 65536)
                throw new IllegalStateException("Unable to read media metadata");
            return parseMetadata(new String(bytes, StandardCharsets.UTF_8));
        } catch (IOException | ExecutionException | TimeoutException exception) {
            throw new IllegalStateException("Unable to read extension media metadata", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Media metadata request interrupted", exception);
        } finally {
            if (process != null && process.isAlive()) process.destroyForcibly();
        }
    }

    public static Map<Integer, String> parseMetadata(String output) {
        Map<Integer, String> result = new LinkedHashMap<>();
        output.lines()
                .forEach(
                        line -> {
                            int separator = line.indexOf('=');
                            if (separator < 0) return;
                            String name = line.substring(0, separator);
                            String value = line.substring(separator + 1).replace("\"", "");
                            if (name.equals("format.duration")) {
                                try {
                                    result.put(
                                            METADATA_KEY_DURATION,
                                            Long.toString(
                                                    new BigDecimal(value)
                                                            .movePointRight(3)
                                                            .longValue()));
                                } catch (NumberFormatException ignored) {
                                }
                            } else if (name.endsWith(".width"))
                                result.putIfAbsent(METADATA_KEY_VIDEO_WIDTH, value);
                            else if (name.endsWith(".height"))
                                result.putIfAbsent(METADATA_KEY_VIDEO_HEIGHT, value);
                        });
        return Map.copyOf(result);
    }

    private void requireOpen() {
        if (closed) throw new IllegalStateException("Metadata retriever is released");
    }
}
