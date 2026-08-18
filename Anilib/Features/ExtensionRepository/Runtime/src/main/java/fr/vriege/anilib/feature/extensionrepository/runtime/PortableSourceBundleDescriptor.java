package fr.vriege.anilib.feature.extensionrepository.runtime;

import fr.vriege.anilib.feature.source.SourceApiVersion;
import fr.vriege.anilib.feature.source.SourceId;
import fr.vriege.anilib.feature.source.SourceNetworkOrigin;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

record PortableSourceBundleDescriptor(
        String packageName,
        long versionCode,
        SourceApiVersion apiVersion,
        String moduleName,
        List<SourceEntry> sources) {
    static final String DESCRIPTOR_PATH = "META-INF/anilib-extension.properties";
    private static final int MAX_ARCHIVE_ENTRIES = 4_096;
    private static final int MAX_DESCRIPTOR_BYTES = 32 * 1_024;
    private static final int MAX_SOURCES = 128;
    private static final Pattern BINARY_NAME = Pattern.compile(
            "[A-Za-z_$][A-Za-z0-9_$]*(?:\\.[A-Za-z_$][A-Za-z0-9_$]*)+");

    PortableSourceBundleDescriptor {
        sources = List.copyOf(sources);
    }

    static PortableSourceBundleDescriptor read(byte[] archive) {
        Properties properties = properties(archive);
        String packageName = required(properties, "package");
        long versionCode = nonNegativeLong(required(properties, "versionCode"), "versionCode");
        SourceApiVersion apiVersion = apiVersion(required(properties, "api"));
        String moduleName = binaryName(required(properties, "module"), "module");
        int sourceCount = positiveInt(required(properties, "source.count"), "source.count", MAX_SOURCES);
        List<SourceEntry> sources = new ArrayList<>(sourceCount);
        Set<SourceId> sourceIds = new LinkedHashSet<>();
        Set<String> components = new LinkedHashSet<>();
        for (int index = 0; index < sourceCount; index++) {
            String prefix = "source." + index + ".";
            SourceId sourceId = SourceId.of(required(properties, prefix + "id"));
            String component = required(properties, prefix + "component");
            fr.vriege.anilib.foundation.component.ComponentId.of(component);
            String displayName = required(properties, prefix + "name");
            String factory = binaryName(required(properties, prefix + "factory"), prefix + "factory");
            Set<SourceNetworkOrigin> origins = origins(properties.getProperty(prefix + "origins", ""));
            if (!sourceIds.add(sourceId) || !components.add(component)) {
                throw new SecurityException("Portable Bundle source identities must be unique");
            }
            sources.add(new SourceEntry(component, displayName, sourceId, factory, origins));
        }
        return new PortableSourceBundleDescriptor(packageName, versionCode, apiVersion, moduleName, sources);
    }

    static Properties properties(byte[] bytes) {
        try (ZipInputStream archive = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            int count = 0;
            Properties descriptor = null;
            while ((entry = archive.getNextEntry()) != null) {
                if (++count > MAX_ARCHIVE_ENTRIES) {
                    throw new SecurityException("Portable Bundle contains too many archive entries");
                }
                if (DESCRIPTOR_PATH.equals(entry.getName())) {
                    if (descriptor != null || entry.isDirectory()) {
                        throw new SecurityException("Portable Bundle descriptor must be one regular entry");
                    }
                    byte[] content = archive.readNBytes(MAX_DESCRIPTOR_BYTES + 1);
                    if (content.length > MAX_DESCRIPTOR_BYTES) {
                        throw new SecurityException("Portable Bundle descriptor exceeds 32 KiB");
                    }
                    descriptor = new Properties();
                    descriptor.load(new StringReader(new String(content, StandardCharsets.UTF_8)));
                }
                archive.closeEntry();
            }
            if (descriptor == null) {
                throw new SecurityException("Portable Bundle descriptor is missing");
            }
            return descriptor;
        } catch (IOException exception) {
            throw new SecurityException("Portable Bundle is not a readable ZIP/JAR archive", exception);
        }
    }

    private static Set<SourceNetworkOrigin> origins(String value) {
        if (value.isBlank()) {
            return Set.of();
        }
        Set<SourceNetworkOrigin> origins = new LinkedHashSet<>();
        for (String part : value.split(",", -1)) {
            URI uri;
            try {
                uri = URI.create(part.strip());
            } catch (IllegalArgumentException exception) {
                throw new SecurityException("Portable Bundle contains an invalid source origin", exception);
            }
            if (uri.getScheme() == null || uri.getHost() == null || uri.getUserInfo() != null
                    || (!uri.getPath().isEmpty() && !uri.getPath().equals("/"))
                    || uri.getQuery() != null || uri.getFragment() != null) {
                throw new SecurityException("Portable Bundle source origin must be one exact HTTP origin");
            }
            int port = uri.getPort();
            if (port < 0) {
                port = "http".equalsIgnoreCase(uri.getScheme()) ? 80 : 443;
            }
            try {
                if (!origins.add(new SourceNetworkOrigin(uri.getScheme(), uri.getHost(), port))) {
                    throw new SecurityException("Portable Bundle source origins must be unique");
                }
            } catch (IllegalArgumentException exception) {
                throw new SecurityException("Portable Bundle contains an invalid source origin", exception);
            }
        }
        return Set.copyOf(origins);
    }

    private static SourceApiVersion apiVersion(String value) {
        String[] parts = value.split("\\.", -1);
        if (parts.length != 2) {
            throw new SecurityException("Portable Bundle api must use major.minor syntax");
        }
        try {
            return new SourceApiVersion(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
        } catch (IllegalArgumentException exception) {
            throw new SecurityException("Portable Bundle api must use major.minor syntax", exception);
        }
    }

    private static String required(Properties properties, String key) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new SecurityException("Portable Bundle descriptor must declare " + key);
        }
        return value.strip();
    }

    private static String binaryName(String value, String key) {
        if (!BINARY_NAME.matcher(value).matches()) {
            throw new SecurityException("Portable Bundle " + key + " must be a qualified Java name");
        }
        return value;
    }

    private static long nonNegativeLong(String value, String key) {
        try {
            long result = Long.parseLong(value);
            if (result < 0) {
                throw new NumberFormatException("negative");
            }
            return result;
        } catch (NumberFormatException exception) {
            throw new SecurityException("Portable Bundle " + key + " must be a non-negative integer", exception);
        }
    }

    private static int positiveInt(String value, String key, int maximum) {
        try {
            int result = Integer.parseInt(value);
            if (result < 1 || result > maximum) {
                throw new NumberFormatException("outside range");
            }
            return result;
        } catch (NumberFormatException exception) {
            throw new SecurityException(
                    "Portable Bundle " + key + " must be between 1 and " + maximum,
                    exception);
        }
    }

    record SourceEntry(
            String componentId,
            String displayName,
            SourceId sourceId,
            String factoryClass,
            Set<SourceNetworkOrigin> origins) {
        SourceEntry {
            origins = Set.copyOf(origins);
        }
    }
}
