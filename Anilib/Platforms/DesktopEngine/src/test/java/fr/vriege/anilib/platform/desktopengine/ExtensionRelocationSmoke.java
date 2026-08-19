package fr.vriege.anilib.platform.desktopengine;

import fr.vriege.anilib.platform.desktopengine.extension.ExtensionBytecodeRelocator;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;

final class ExtensionRelocationSmoke {
    private static final String ORIGINAL = "eu/kanade/tachiyomi/source/Source";
    private static final String RELOCATED =
            "fr/vriege/anilib/platform/desktopengine/compat/aniyomi/source/Source";

    private ExtensionRelocationSmoke() {
    }

    static void verify() throws IOException {
        Path directory = Files.createTempDirectory("anilib-extension-relocation-");
        try {
            Path input = directory.resolve("input.jar");
            Path output = directory.resolve("output.jar");
            writeInput(input);
            ExtensionBytecodeRelocator.RelocationResult result =
                    new ExtensionBytecodeRelocator().relocate(input, output);
            if (result.relocatedClasses() != 1 || !result.unresolvedPrefixes().isEmpty()) {
                throw new IllegalStateException("Extension ABI relocation result is invalid: " + result);
            }
            try (JarFile jar = new JarFile(output.toFile())) {
                JarEntry entry = jar.getJarEntry("sample/Extension.class");
                byte[] bytes = jar.getInputStream(entry).readAllBytes();
                String constants = new String(bytes, StandardCharsets.ISO_8859_1);
                if (!constants.contains(RELOCATED) || constants.contains(ORIGINAL)
                        || !"sample/Extension".equals(new ClassReader(bytes).getClassName())) {
                    throw new IllegalStateException("Extension ABI was not relocated in class bytecode");
                }
                if (jar.getJarEntry("META-INF/anilib-desktop-extension.properties") == null) {
                    throw new IllegalStateException("Prepared extension marker is missing");
                }
            }
        } finally {
            try (var paths = Files.walk(directory)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(path);
                }
            }
        }
    }

    private static void writeInput(Path input) throws IOException {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "sample/Extension", null, "java/lang/Object", null);
        writer.visitField(Opcodes.ACC_PRIVATE, "source", 'L' + ORIGINAL + ';', null, null).visitEnd();
        writer.visitField(Opcodes.ACC_PRIVATE, "sourceClass", "Ljava/lang/String;", null,
                ORIGINAL.replace('/', '.')).visitEnd();
        writer.visitEnd();
        try (JarOutputStream jar = new JarOutputStream(Files.newOutputStream(input))) {
            jar.putNextEntry(new JarEntry("sample/Extension.class"));
            jar.write(writer.toByteArray());
            jar.closeEntry();
        }
    }
}
