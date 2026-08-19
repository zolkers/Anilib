package fr.vriege.anilib.platform.desktopengine.extension;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;

public final class ExtensionBytecodeRelocator {
    private static final int MAX_ENTRIES = 25_000;
    private static final long MAX_EXPANDED_BYTES = 256L * 1024L * 1024L;
    private static final String TARGET = "fr/vriege/anilib/platform/desktopengine/compat/";
    private static final Map<String, String> PREFIXES = Map.ofEntries(
            Map.entry("eu/kanade/tachiyomi/source/", TARGET + "aniyomi/source/"),
            Map.entry("eu/kanade/tachiyomi/animesource/", TARGET + "aniyomi/animesource/"),
            Map.entry("eu/kanade/tachiyomi/network/", TARGET + "aniyomi/network/"),
            Map.entry("eu/kanade/tachiyomi/util/", TARGET + "aniyomi/util/"),
            Map.entry("eu/kanade/tachiyomi/torrentutils/", TARGET + "aniyomi/torrentutils/"),
            Map.entry("tachiyomi/core/", TARGET + "tachiyomi/core/"),
            Map.entry("aniyomi/core/", TARGET + "aniyomi/core/"),
            Map.entry("androidx/preference/", TARGET + "androidx/preference/"),
            Map.entry("android/", TARGET + "android/"),
            Map.entry("app/cash/quickjs/", TARGET + "quickjs/"),
            Map.entry("com/squareup/duktape/", TARGET + "duktape/"),
            Map.entry("logcat/", TARGET + "logcat/"),
            Map.entry("mihon/core/", TARGET + "mihon/core/"));
    private static final List<String> FORBIDDEN = List.copyOf(PREFIXES.keySet());

    public RelocationResult relocate(Path inputJar, Path outputJar) {
        Path input = inputJar.toAbsolutePath().normalize();
        Path output = outputJar.toAbsolutePath().normalize();
        Map<String, byte[]> entries = readEntries(input);
        Map<String, byte[]> transformed = new LinkedHashMap<>();
        Set<String> unresolved = new HashSet<>();
        int relocatedClasses = 0;
        int repairs = 0;
        for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
            String name = entry.getKey();
            byte[] bytes = entry.getValue();
            if (!name.endsWith(".class")) {
                if (!signature(name)) {
                    putUnique(transformed, name, bytes);
                }
                continue;
            }
            Transformation transformation = transform(bytes);
            if (!java.util.Arrays.equals(bytes, transformation.bytes())) {
                relocatedClasses++;
            }
            repairs += transformation.repairs();
            collectUnresolved(transformation.bytes(), unresolved);
            String className = new ClassReader(transformation.bytes()).getClassName() + ".class";
            putUnique(transformed, className, transformation.bytes());
        }
        putUnique(transformed, "META-INF/anilib-desktop-extension.properties",
                ("format=1\nrelocatedClasses=" + relocatedClasses + "\nrepairs=" + repairs + "\n")
                        .getBytes(StandardCharsets.UTF_8));
        writeEntries(output, transformed);
        return new RelocationResult(relocatedClasses, repairs, unresolved.stream().sorted().toList());
    }

    private static Transformation transform(byte[] source) {
        ClassNode node = new ClassNode();
        new ClassReader(source).accept(node, 0);
        int repairs = repairWrongConstructorOwners(node);
        ClassWriter writer = new ClassWriter(0);
        node.accept(new ClassRemapper(writer, new AbiRemapper()));
        return new Transformation(writer.toByteArray(), repairs);
    }

    private static int repairWrongConstructorOwners(ClassNode owner) {
        int repairs = 0;
        for (MethodNode method : owner.methods) {
            ArrayDeque<TypeInsnNode> pending = new ArrayDeque<>();
            TypeInsnNode created = null;
            for (AbstractInsnNode instruction : method.instructions) {
                if (instruction instanceof TypeInsnNode type && type.getOpcode() == Opcodes.NEW) {
                    created = type;
                } else if (instruction instanceof InsnNode simple
                        && simple.getOpcode() == Opcodes.DUP && created != null) {
                    pending.push(created);
                    created = null;
                } else if (instruction instanceof MethodInsnNode call
                        && call.getOpcode() == Opcodes.INVOKESPECIAL
                        && "<init>".equals(call.name)
                        && !pending.isEmpty()) {
                    String expectedOwner = pending.pop().desc;
                    if (!expectedOwner.equals(call.owner)) {
                        call.owner = expectedOwner;
                        repairs++;
                    }
                }
            }
        }
        return repairs;
    }

    private static Map<String, byte[]> readEntries(Path jar) {
        if (!Files.isRegularFile(jar) || Files.isSymbolicLink(jar)) {
            throw new IllegalArgumentException("Converted extension must be a regular non-link JAR");
        }
        Map<String, byte[]> result = new LinkedHashMap<>();
        long expanded = 0;
        int count = 0;
        try (JarFile file = new JarFile(jar.toFile())) {
            Enumeration<JarEntry> entries = file.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (entry.isDirectory()) {
                    continue;
                }
                count++;
                if (count > MAX_ENTRIES) {
                    throw new IllegalArgumentException("Converted extension contains too many entries");
                }
                String name = safeEntryName(entry.getName());
                byte[] bytes;
                try (InputStream input = file.getInputStream(entry)) {
                    bytes = input.readNBytes(Math.toIntExact(MAX_EXPANDED_BYTES + 1));
                }
                expanded = Math.addExact(expanded, bytes.length);
                if (bytes.length > MAX_EXPANDED_BYTES || expanded > MAX_EXPANDED_BYTES) {
                    throw new IllegalArgumentException("Converted extension exceeds the expanded size limit");
                }
                putUnique(result, name, bytes);
            }
            return result;
        } catch (IOException exception) {
            throw new IllegalArgumentException("Unable to read converted extension JAR", exception);
        }
    }

    private static void writeEntries(Path output, Map<String, byte[]> entries) {
        try {
            Files.createDirectories(output.getParent());
            try (OutputStream file = Files.newOutputStream(output);
                    JarOutputStream jar = new JarOutputStream(file)) {
                for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                    JarEntry jarEntry = new JarEntry(entry.getKey());
                    jarEntry.setTime(0L);
                    jar.putNextEntry(jarEntry);
                    jar.write(entry.getValue());
                    jar.closeEntry();
                }
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to write relocated extension JAR", exception);
        }
    }

    private static String safeEntryName(String name) {
        if (name.isBlank() || name.startsWith("/") || name.startsWith("\\")
                || name.contains("../") || name.contains("..\\") || name.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("Unsafe archive entry: " + name);
        }
        return name.replace('\\', '/');
    }

    private static boolean signature(String name) {
        String upper = name.toUpperCase(java.util.Locale.ROOT);
        return upper.startsWith("META-INF/")
                && (upper.endsWith(".SF") || upper.endsWith(".RSA") || upper.endsWith(".DSA"));
    }

    private static void putUnique(Map<String, byte[]> entries, String name, byte[] bytes) {
        if (entries.putIfAbsent(name, bytes) != null) {
            throw new IllegalArgumentException("Duplicate archive entry after relocation: " + name);
        }
    }

    private static void collectUnresolved(byte[] bytes, Set<String> unresolved) {
        String constantPool = new String(bytes, StandardCharsets.ISO_8859_1);
        for (String prefix : FORBIDDEN) {
            if (constantPool.contains(prefix) || constantPool.contains(prefix.replace('/', '.'))) {
                unresolved.add(prefix);
            }
        }
    }

    public record RelocationResult(int relocatedClasses, int repairedInstructions, List<String> unresolvedPrefixes) {
        public RelocationResult {
            unresolvedPrefixes = List.copyOf(unresolvedPrefixes);
        }
    }

    private record Transformation(byte[] bytes, int repairs) {
    }

    private static final class AbiRemapper extends Remapper {
        private AbiRemapper() {
            super(Opcodes.ASM9);
        }

        @Override
        public String map(String internalName) {
            for (Map.Entry<String, String> entry : PREFIXES.entrySet()) {
                if (internalName.startsWith(entry.getKey())) {
                    return entry.getValue() + internalName.substring(entry.getKey().length());
                }
            }
            return internalName;
        }

        @Override
        public Object mapValue(Object value) {
            if (value instanceof String text) {
                String internal = text.replace('.', '/');
                String mapped = map(internal);
                if (!mapped.equals(internal)) {
                    return text.indexOf('/') >= 0 ? mapped : mapped.replace('/', '.');
                }
            }
            return super.mapValue(value);
        }
    }
}
