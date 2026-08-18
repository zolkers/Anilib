package fr.vriege.anilib.feature.extensionrepository.runtime;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.module.ModuleDescriptor;
import java.lang.module.ModuleFinder;
import java.lang.module.ModuleReader;
import java.lang.module.ModuleReference;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Bounded module archive that avoids retaining an operating-system handle to an installed JAR. */
final class InMemoryModuleArchive {
    private static final int MAX_ENTRY_BYTES = 8 * 1_024 * 1_024;
    private static final int MAX_EXPANDED_BYTES = 32 * 1_024 * 1_024;
    private static final int MAX_ENTRIES = 4_096;

    private InMemoryModuleArchive() {
    }

    static ModuleFinder finder(byte[] archive) {
        Map<String, byte[]> entries = entries(archive);
        byte[] moduleInfo = entries.get("module-info.class");
        if (moduleInfo == null) {
            throw new SecurityException("Portable Bundle must contain module-info.class");
        }
        ModuleDescriptor descriptor;
        try {
            descriptor = ModuleDescriptor.read(ByteBuffer.wrap(moduleInfo));
        } catch (RuntimeException exception) {
            throw new SecurityException("Portable Bundle contains an invalid module descriptor", exception);
        }
        ModuleReference reference = new MemoryModuleReference(descriptor, entries);
        return new ModuleFinder() {
            @Override
            public Optional<ModuleReference> find(String name) {
                return descriptor.name().equals(name) ? Optional.of(reference) : Optional.empty();
            }

            @Override
            public Set<ModuleReference> findAll() {
                return Set.of(reference);
            }
        };
    }

    private static Map<String, byte[]> entries(byte[] bytes) {
        try (ZipInputStream archive = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            Map<String, byte[]> entries = new LinkedHashMap<>();
            ZipEntry entry;
            int count = 0;
            int expanded = 0;
            while ((entry = archive.getNextEntry()) != null) {
                if (++count > MAX_ENTRIES) {
                    throw new SecurityException("Portable Bundle contains too many archive entries");
                }
                String name = entry.getName();
                if (name.startsWith("META-INF/versions/") || name.startsWith("/") || name.contains("../")) {
                    throw new SecurityException("Portable Bundle contains an unsupported archive path");
                }
                if (!entry.isDirectory()) {
                    byte[] content = archive.readNBytes(MAX_ENTRY_BYTES + 1);
                    if (content.length > MAX_ENTRY_BYTES || expanded > MAX_EXPANDED_BYTES - content.length) {
                        throw new SecurityException("Portable Bundle expands beyond its bounded module size");
                    }
                    expanded += content.length;
                    if (entries.putIfAbsent(name, content) != null) {
                        throw new SecurityException("Portable Bundle contains duplicate archive entries");
                    }
                }
                archive.closeEntry();
            }
            return Map.copyOf(entries);
        } catch (IOException exception) {
            throw new SecurityException("Portable Bundle is not a readable module archive", exception);
        }
    }

    private static final class MemoryModuleReference extends ModuleReference {
        private final Map<String, byte[]> entries;

        private MemoryModuleReference(ModuleDescriptor descriptor, Map<String, byte[]> entries) {
            super(descriptor, URI.create("memory:/" + descriptor.name()));
            this.entries = entries;
        }

        @Override
        public ModuleReader open() {
            return new MemoryModuleReader(entries);
        }
    }

    private static final class MemoryModuleReader implements ModuleReader {
        private final Map<String, byte[]> entries;

        private MemoryModuleReader(Map<String, byte[]> entries) {
            this.entries = entries;
        }

        @Override
        public Optional<URI> find(String name) {
            return entries.containsKey(name)
                    ? Optional.of(URI.create("memory:/" + name.replace(" ", "%20")))
                    : Optional.empty();
        }

        @Override
        public Optional<InputStream> open(String name) {
            byte[] content = entries.get(name);
            return content == null
                    ? Optional.empty()
                    : Optional.of(new ByteArrayInputStream(content));
        }

        @Override
        public Optional<ByteBuffer> read(String name) {
            byte[] content = entries.get(name);
            return content == null ? Optional.empty() : Optional.of(ByteBuffer.wrap(content));
        }

        @Override
        public Stream<String> list() {
            return entries.keySet().stream();
        }

        @Override
        public void close() {
        }
    }
}
