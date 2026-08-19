package fr.vriege.anilib.tooling.sourcepublisher;

import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Properties;
import java.util.regex.Pattern;

record SourcePackageConfiguration(
        Path configurationFile,
        Path bundle,
        Optional<Path> apk,
        String displayName,
        String packageName,
        String language,
        long versionCode,
        String versionName,
        boolean adult,
        String kind,
        String apiVersion,
        String keyId,
        List<SourceEntry> sources) {
    private static final Pattern QUALIFIED_NAME = Pattern.compile(
            "[A-Za-z_$][A-Za-z0-9_$]*(?:\\.[A-Za-z_$][A-Za-z0-9_$]*)+");

    SourcePackageConfiguration {
        apk = java.util.Objects.requireNonNull(apk, "apk must not be null");
        sources = List.copyOf(sources);
    }

    static SourcePackageConfiguration read(Path configurationFile) {
        Path file = configurationFile.toAbsolutePath().normalize();
        Properties properties = new Properties();
        try {
            properties.load(new StringReader(new String(
                    PublisherFiles.read(file, "package configuration"),
                    StandardCharsets.UTF_8)));
        } catch (IOException exception) {
            throw new IllegalArgumentException("Invalid package properties: " + file, exception);
        }
        Path parent = file.getParent();
        Path bundle = parent.resolve(required(properties, "bundle")).normalize();
        Optional<Path> apk = optional(properties, "apk").map(value -> parent.resolve(value).normalize());
        apk.ifPresent(path -> {
            if (!path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".apk")) {
                throw new IllegalArgumentException("apk must point to an .apk file");
            }
        });
        String packageName = packageIdentifier(required(properties, "package"));
        long versionCode = positiveLong(required(properties, "versionCode"), "versionCode");
        int sourceCount = positiveInt(required(properties, "source.count"), "source.count", 128);
        List<SourceEntry> sources = new ArrayList<>();
        for (int index = 0; index < sourceCount; index++) {
            String prefix = "source." + index + ".";
            sources.add(new SourceEntry(
                    required(properties, prefix + "id"),
                    required(properties, prefix + "name"),
                    required(properties, prefix + "lang"),
                    optionalUri(properties.getProperty(prefix + "baseUrl")),
                    qualifiedName(required(properties, prefix + "factory"), prefix + "factory")));
        }
        return new SourcePackageConfiguration(
                file,
                bundle,
                apk,
                required(properties, "name"),
                packageName,
                required(properties, "lang"),
                versionCode,
                required(properties, "version"),
                bool(properties.getProperty("adult", "false"), "adult"),
                kind(required(properties, "kind")),
                api(required(properties, "api")),
                required(properties, "keyId"),
                sources);
    }

    String artifactName() {
        return "anilib-source-" + packageHash(packageName) + "-v" + versionCode + ".jar";
    }

    String apkArtifactName() {
        if (apk.isEmpty()) {
            throw new IllegalStateException("Package does not declare an APK artifact");
        }
        return "anilib-android-" + packageHash(packageName) + "-v" + versionCode + ".apk";
    }

    private static String required(Properties properties, String name) {
        String value = properties.getProperty(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Package configuration must declare " + name);
        }
        return value.strip();
    }

    private static Optional<String> optional(Properties properties, String name) {
        String value = properties.getProperty(name);
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(value.strip());
    }

    private static String qualifiedName(String value, String name) {
        if (!QUALIFIED_NAME.matcher(value).matches()) {
            throw new IllegalArgumentException(name + " must be a qualified Java name");
        }
        return value;
    }

    private static String packageIdentifier(String value) {
        if (value.length() > 512) {
            throw new IllegalArgumentException("package must not exceed 512 characters");
        }
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (Character.isISOControl(character) || Character.isSurrogate(character)) {
                throw new IllegalArgumentException("package must contain printable Unicode text");
            }
        }
        return value;
    }

    private static boolean bool(String value, String name) {
        if (value.equalsIgnoreCase("true")) {
            return true;
        }
        if (value.equalsIgnoreCase("false")) {
            return false;
        }
        throw new IllegalArgumentException(name + " must be true or false");
    }

    private static String packageHash(String packageName) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(packageName.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 16);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JDK 21 does not provide SHA-256", exception);
        }
    }

    private static String kind(String value) {
        String normalized = value.toLowerCase(Locale.ROOT);
        if (!normalized.equals("anime") && !normalized.equals("manga")) {
            throw new IllegalArgumentException("kind must be anime or manga");
        }
        return normalized;
    }

    private static String api(String value) {
        if (!value.matches("[1-9][0-9]*\\.[0-9]+")) {
            throw new IllegalArgumentException("api must use major.minor syntax");
        }
        return value;
    }

    private static URI optionalUri(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        URI uri = URI.create(value.strip()).normalize();
        if (!"https".equalsIgnoreCase(uri.getScheme())
                || uri.getHost() == null
                || uri.getUserInfo() != null
                || uri.getFragment() != null) {
            throw new IllegalArgumentException("source baseUrl must be an absolute HTTPS URI");
        }
        return uri;
    }

    private static long positiveLong(String value, String name) {
        try {
            long result = Long.parseLong(value);
            if (result < 1) {
                throw new NumberFormatException("not positive");
            }
            return result;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(name + " must be a positive integer", exception);
        }
    }

    private static int positiveInt(String value, String name, int maximum) {
        long result = positiveLong(value, name);
        if (result > maximum) {
            throw new IllegalArgumentException(name + " must not exceed " + maximum);
        }
        return Math.toIntExact(result);
    }

    record SourceEntry(String id, String name, String language, URI baseUri, String factoryClass) {
    }
}
