package fr.vriege.anilib.platform.desktopextensionhost;

import fr.vriege.anilib.platform.desktopextensionhost.extension.ExtensionBytecodeRelocator;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.net.URL;

final class ExtensionRelocationSmoke {
    private static final String ORIGINAL = "eu/kanade/tachiyomi/source/Source";
    private static final String RELOCATED =
            "fr/vriege/anilib/platform/desktopextensionhost/compat/aniyomi/source/Source";

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
            if (result.relocatedClasses() != 2 || !result.unresolvedPrefixes().isEmpty()) {
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
            try (URLClassLoader loader = new URLClassLoader(new URL[]{output.toUri().toURL()})) {
                Class<?> enumType = Class.forName("sample.BrokenEnum", true, loader);
                if (enumType.getField("ITEM").get(null) == null) {
                    throw new IllegalStateException("Converted enum constant was not repaired");
                }
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("Converted enum cannot be loaded", exception);
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
            writeBrokenEnum(jar);
        }
    }

    private static void writeBrokenEnum(JarOutputStream jar) throws IOException {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL | Opcodes.ACC_ENUM,
                "sample/BrokenEnum", "Ljava/lang/Enum<Lsample/BrokenEnum;>;", "java/lang/Enum", null);
        writer.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL | Opcodes.ACC_ENUM,
                "ITEM", "Lsample/BrokenEnum;", null, null).visitEnd();
        var initializer = writer.visitMethod(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null);
        initializer.visitCode();
        initializer.visitTypeInsn(Opcodes.NEW, "java/lang/Enum");
        initializer.visitInsn(Opcodes.DUP);
        initializer.visitLdcInsn("ITEM");
        initializer.visitInsn(Opcodes.ICONST_0);
        initializer.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Enum", "<init>",
                "(Ljava/lang/String;I)V", false);
        initializer.visitFieldInsn(Opcodes.PUTSTATIC, "sample/BrokenEnum", "ITEM", "Lsample/BrokenEnum;");
        initializer.visitInsn(Opcodes.RETURN);
        initializer.visitMaxs(4, 0);
        initializer.visitEnd();
        writer.visitEnd();
        jar.putNextEntry(new JarEntry("sample/BrokenEnum.class"));
        jar.write(writer.toByteArray());
        jar.closeEntry();
    }
}
