package fr.vriege.anilib.platform.desktopextensionhost;

import fr.vriege.anilib.platform.desktopextensionhost.extension.ExtensionApkMetadata;
import fr.vriege.anilib.platform.desktopextensionhost.extension.ExtensionKind;
import fr.vriege.anilib.platform.desktopextensionhost.extension.ExtensionRegistry;
import fr.vriege.anilib.platform.desktopextensionhost.extension.ExtensionRuntimeCatalog;
import fr.vriege.anilib.platform.desktopextensionhost.extension.InstalledExtension;
import fr.vriege.anilib.platform.desktopextensionhost.extension.LoadedSource;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

final class ExtensionRuntimeCatalogSmoke {
    private static final String SOURCE_CLASS = "sample.DynamicSource";
    private static final String FACTORY_CLASS = "sample.DynamicFactory";
    private static final String ANIME_SOURCE =
            "fr/vriege/anilib/platform/desktopextensionhost/compat/aniyomi/animesource/AnimeSource";

    private ExtensionRuntimeCatalogSmoke() {
    }

    static void verify() throws IOException {
        Path directory = Files.createTempDirectory("anilib-extension-runtime-");
        try {
            Path archive = directory.resolve("extension.jar");
            writeArchive(archive);
            ExtensionApkMetadata metadata = new ExtensionApkMetadata(
                    "org.example.dynamic",
                    "Dynamic",
                    "1.0",
                    1,
                    ExtensionKind.ANIME,
                    false,
                    List.of(FACTORY_CLASS),
                    Optional.empty());
            InstalledExtension installed = new InstalledExtension(
                    metadata, directory.resolve("extension.apk"), archive);
            ExtensionRuntimeCatalog catalog = new ExtensionRuntimeCatalog(new ExtensionRegistry(directory));
            try (ExtensionRuntimeCatalog.Snapshot snapshot = catalog.discover(List.of(installed))) {
                if (!snapshot.failures().isEmpty() || snapshot.sources().size() != 1) {
                    throw new IllegalStateException("Dynamic extension discovery failed: " + snapshot.failures());
                }
                LoadedSource source = snapshot.sources().getFirst();
                if (source.id() != 42L || !source.name().equals("Dynamic source")
                        || !source.language().equals("fr") || source.kind() != ExtensionKind.ANIME) {
                    throw new IllegalStateException("Dynamic source descriptor is invalid: " + source);
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

    private static void writeArchive(Path archive) throws IOException {
        byte[] source = sourceClass();
        byte[] factory = factoryClass();
        try (JarOutputStream jar = new JarOutputStream(Files.newOutputStream(archive))) {
            jar.putNextEntry(new JarEntry(SOURCE_CLASS.replace('.', '/') + ".class"));
            jar.write(source);
            jar.closeEntry();
            jar.putNextEntry(new JarEntry(FACTORY_CLASS.replace('.', '/') + ".class"));
            jar.write(factory);
            jar.closeEntry();
        }
    }

    private static byte[] sourceClass() {
        ClassWriter writer = new ClassWriter(0);
        String name = SOURCE_CLASS.replace('.', '/');
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, name, null, "java/lang/Object",
                new String[]{ANIME_SOURCE});
        MethodVisitor constructor = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        constructor.visitCode();
        constructor.visitVarInsn(Opcodes.ALOAD, 0);
        constructor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        constructor.visitInsn(Opcodes.RETURN);
        constructor.visitMaxs(1, 1);
        constructor.visitEnd();
        constantLong(writer, "getId", 42L);
        constantText(writer, "getName", "Dynamic source");
        constantText(writer, "getLang", "fr");
        constantBoolean(writer, "getSupportsLatest", true);
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static byte[] factoryClass() {
        ClassWriter writer = new ClassWriter(0);
        String name = FACTORY_CLASS.replace('.', '/');
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, name, null, "java/lang/Object", null);
        MethodVisitor constructor = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        constructor.visitCode();
        constructor.visitVarInsn(Opcodes.ALOAD, 0);
        constructor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        constructor.visitInsn(Opcodes.RETURN);
        constructor.visitMaxs(1, 1);
        constructor.visitEnd();
        MethodVisitor factory = writer.visitMethod(
                Opcodes.ACC_PUBLIC, "createSources", "()Ljava/util/List;", null, null);
        factory.visitCode();
        factory.visitTypeInsn(Opcodes.NEW, SOURCE_CLASS.replace('.', '/'));
        factory.visitInsn(Opcodes.DUP);
        factory.visitMethodInsn(
                Opcodes.INVOKESPECIAL, SOURCE_CLASS.replace('.', '/'), "<init>", "()V", false);
        factory.visitMethodInsn(
                Opcodes.INVOKESTATIC, "java/util/List", "of",
                "(Ljava/lang/Object;)Ljava/util/List;", true);
        factory.visitInsn(Opcodes.ARETURN);
        factory.visitMaxs(2, 1);
        factory.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static void constantLong(ClassWriter writer, String name, long value) {
        MethodVisitor method = writer.visitMethod(Opcodes.ACC_PUBLIC, name, "()J", null, null);
        method.visitCode();
        method.visitLdcInsn(value);
        method.visitInsn(Opcodes.LRETURN);
        method.visitMaxs(2, 1);
        method.visitEnd();
    }

    private static void constantText(ClassWriter writer, String name, String value) {
        MethodVisitor method = writer.visitMethod(
                Opcodes.ACC_PUBLIC, name, "()Ljava/lang/String;", null, null);
        method.visitCode();
        method.visitLdcInsn(value);
        method.visitInsn(Opcodes.ARETURN);
        method.visitMaxs(1, 1);
        method.visitEnd();
    }

    private static void constantBoolean(ClassWriter writer, String name, boolean value) {
        MethodVisitor method = writer.visitMethod(Opcodes.ACC_PUBLIC, name, "()Z", null, null);
        method.visitCode();
        method.visitInsn(value ? Opcodes.ICONST_1 : Opcodes.ICONST_0);
        method.visitInsn(Opcodes.IRETURN);
        method.visitMaxs(1, 1);
        method.visitEnd();
    }
}
