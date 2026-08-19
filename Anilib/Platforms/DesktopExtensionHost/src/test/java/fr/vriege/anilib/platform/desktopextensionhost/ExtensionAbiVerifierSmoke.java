package fr.vriege.anilib.platform.desktopextensionhost;

import fr.vriege.anilib.platform.desktopextensionhost.extension.ExtensionAbiVerifier;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

final class ExtensionAbiVerifierSmoke {
    private static final String QUICK_JS =
            "fr/vriege/anilib/platform/desktopextensionhost/compat/quickjs/QuickJs";
    private static final String LOG =
            "fr/vriege/anilib/platform/desktopextensionhost/compat/android/util/Log";
    private static final String LIST_PREFERENCE =
            "fr/vriege/anilib/platform/desktopextensionhost/compat/androidx/preference/ListPreference";
    private static final String OK_HTTP_EXTENSIONS =
            "fr/vriege/anilib/platform/desktopextensionhost/compat/aniyomi/network/OkHttpExtensionsKt";
    private static final String HTTP_SOURCE =
            "fr/vriege/anilib/platform/desktopextensionhost/compat/aniyomi/source/online/HttpSource";
    private static final String FILTER_LIST =
            "fr/vriege/anilib/platform/desktopextensionhost/compat/aniyomi/source/model/FilterList";

    private ExtensionAbiVerifierSmoke() {
    }

    static void verify() throws IOException {
        Path directory = Files.createTempDirectory("anilib-extension-abi-");
        try {
            Path supported = directory.resolve("supported.jar");
            writeArchive(supported, "evaluate", "(Ljava/lang/String;)Ljava/lang/Object;");
            ExtensionAbiVerifier verifier = new ExtensionAbiVerifier();
            if (!verifier.inspect(supported).compatible()) {
                throw new IllegalStateException("Supported QuickJS ABI was rejected");
            }

            Path unsupported = directory.resolve("unsupported.jar");
            writeArchive(unsupported, "missingOperation", "()V");
            ExtensionAbiVerifier.Report report = verifier.inspect(unsupported);
            if (report.compatible() || report.missingSymbols().stream()
                    .noneMatch(symbol -> symbol.contains("missingOperation()V"))) {
                throw new IllegalStateException("Missing host ABI method was not reported: " + report);
            }

            Path mangaDexSurface = directory.resolve("mangadex-surface.jar");
            writeMangaDexSurfaceArchive(mangaDexSurface);
            ExtensionAbiVerifier.Report mangaDexReport = verifier.inspect(mangaDexSurface);
            if (!mangaDexReport.compatible()) {
                throw new IllegalStateException("MangaDex host ABI surface was rejected: " + mangaDexReport);
            }

            String installed = System.getenv("ANILIB_DESKTOP_EXTENSION_ARCHIVE");
            if (installed != null && !installed.isBlank()) {
                ExtensionAbiVerifier.Report installedReport = verifier.inspect(Path.of(installed));
                if (!installedReport.compatible()) {
                    throw new IllegalStateException("Installed extension ABI gaps: " + installedReport);
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

    private static void writeArchive(Path archive, String methodName, String descriptor) throws IOException {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "sample/AbiConsumer", null, "java/lang/Object", null);
        MethodVisitor method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "invoke", "()V", null, null);
        method.visitCode();
        if ("evaluate".equals(methodName)) {
            method.visitMethodInsn(Opcodes.INVOKESTATIC, QUICK_JS, "create", "()L" + QUICK_JS + ";", false);
            method.visitLdcInsn("var eps1 = [];");
            method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, QUICK_JS, methodName, descriptor, false);
            method.visitInsn(Opcodes.POP);
        } else {
            method.visitInsn(Opcodes.ACONST_NULL);
            method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, QUICK_JS, methodName, descriptor, false);
        }
        method.visitInsn(Opcodes.RETURN);
        method.visitMaxs(2, 0);
        method.visitEnd();
        writer.visitEnd();
        try (JarOutputStream jar = new JarOutputStream(Files.newOutputStream(archive))) {
            jar.putNextEntry(new JarEntry("sample/AbiConsumer.class"));
            jar.write(writer.toByteArray());
            jar.closeEntry();
        }
    }

    private static void writeMangaDexSurfaceArchive(Path archive) throws IOException {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "sample/MangaDexAbiConsumer",
                null, "java/lang/Object", null);
        MethodVisitor method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "invoke", "()V", null, null);
        method.visitCode();
        method.visitInsn(Opcodes.ACONST_NULL);
        method.visitInsn(Opcodes.ACONST_NULL);
        method.visitMethodInsn(Opcodes.INVOKESTATIC, LOG, "wtf",
                "(Ljava/lang/String;Ljava/lang/String;)I", false);
        method.visitInsn(Opcodes.POP);
        method.visitInsn(Opcodes.ACONST_NULL);
        method.visitInsn(Opcodes.ACONST_NULL);
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, LIST_PREFERENCE, "findIndexOfValue",
                "(Ljava/lang/String;)I", false);
        method.visitInsn(Opcodes.POP);
        method.visitInsn(Opcodes.ACONST_NULL);
        method.visitMethodInsn(Opcodes.INVOKESTATIC, OK_HTTP_EXTENSIONS, "asObservable",
                "(Lokhttp3/Call;)Lrx/Observable;", false);
        method.visitInsn(Opcodes.POP);
        method.visitInsn(Opcodes.ACONST_NULL);
        method.visitMethodInsn(Opcodes.INVOKESTATIC, OK_HTTP_EXTENSIONS, "asObservableSuccess",
                "(Lokhttp3/Call;)Lrx/Observable;", false);
        method.visitInsn(Opcodes.POP);
        method.visitInsn(Opcodes.ACONST_NULL);
        method.visitInsn(Opcodes.ICONST_1);
        method.visitLdcInsn("query");
        method.visitInsn(Opcodes.ACONST_NULL);
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, HTTP_SOURCE, "fetchSearchManga",
                "(ILjava/lang/String;L" + FILTER_LIST + ";)Lrx/Observable;", false);
        method.visitInsn(Opcodes.POP);
        method.visitInsn(Opcodes.RETURN);
        method.visitMaxs(0, 0);
        method.visitEnd();
        writer.visitEnd();
        try (JarOutputStream jar = new JarOutputStream(Files.newOutputStream(archive))) {
            jar.putNextEntry(new JarEntry("sample/MangaDexAbiConsumer.class"));
            jar.write(writer.toByteArray());
            jar.closeEntry();
        }
    }
}
