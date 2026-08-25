package fr.vriege.anilib.platform.desktopextensionhost;

import fr.vriege.anilib.platform.desktopextensionhost.compat.android.app.Application;
import fr.vriege.anilib.platform.desktopextensionhost.compat.android.os.Handler;
import fr.vriege.anilib.platform.desktopextensionhost.compat.android.os.Looper;
import fr.vriege.anilib.platform.desktopextensionhost.compat.android.webkit.WebResourceRequest;
import fr.vriege.anilib.platform.desktopextensionhost.compat.android.webkit.WebResourceResponse;
import fr.vriege.anilib.platform.desktopextensionhost.compat.android.webkit.WebView;
import fr.vriege.anilib.platform.desktopextensionhost.compat.android.webkit.WebViewClient;
import fr.vriege.anilib.platform.desktopextensionhost.extension.ExtensionAbiVerifier;
import fr.vriege.anilib.platform.desktopextensionhost.extension.ExtensionRegistry;
import fr.vriege.anilib.platform.desktopextensionhost.extension.ExtensionRuntimeCatalog;
import fr.vriege.anilib.platform.desktopextensionhost.extension.ExtensionSourceOperations;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
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
    private static final String CONTEXT_WRAPPER =
            "fr/vriege/anilib/platform/desktopextensionhost/compat/android/content/ContextWrapper";
    private static final String S_CHAPTER =
            "fr/vriege/anilib/platform/desktopextensionhost/compat/aniyomi/source/model/SChapter";
    private static final String S_MANGA =
            "fr/vriege/anilib/platform/desktopextensionhost/compat/aniyomi/source/model/SManga";
    private static final String S_MANGA_UPDATE =
            "fr/vriege/anilib/platform/desktopextensionhost/compat/aniyomi/source/model/SMangaUpdate";
    private static final String HANDLER =
            "fr/vriege/anilib/platform/desktopextensionhost/compat/android/os/Handler";
    private static final String LOOPER =
            "fr/vriege/anilib/platform/desktopextensionhost/compat/android/os/Looper";
    private static final String WEB_VIEW =
            "fr/vriege/anilib/platform/desktopextensionhost/compat/android/webkit/WebView";
    private static final String WEB_VIEW_CLIENT =
            "fr/vriege/anilib/platform/desktopextensionhost/compat/android/webkit/WebViewClient";
    private static final String WEB_SETTINGS =
            "fr/vriege/anilib/platform/desktopextensionhost/compat/android/webkit/WebSettings";

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

            Path combinedUpdateSurface = directory.resolve("combined-update-surface.jar");
            writeCombinedUpdateSurfaceArchive(combinedUpdateSurface);
            ExtensionAbiVerifier.Report combinedUpdateReport = verifier.inspect(combinedUpdateSurface);
            if (!combinedUpdateReport.compatible()) {
                throw new IllegalStateException(
                        "Combined manga update ABI surface was rejected: " + combinedUpdateReport);
            }

            Path animeSamaWebViewSurface = directory.resolve("anime-sama-webview-surface.jar");
            writeAnimeSamaWebViewSurfaceArchive(animeSamaWebViewSurface);
            ExtensionAbiVerifier.Report animeSamaReport = verifier.inspect(animeSamaWebViewSurface);
            if (!animeSamaReport.compatible()) {
                throw new IllegalStateException("Anime-Sama WebView ABI surface was rejected: " + animeSamaReport);
            }
            verifyWebViewSurfaceBehavior();

            String abiArchive = System.getenv("ANILIB_DESKTOP_EXTENSION_ABI_ARCHIVE");
            if (abiArchive != null && !abiArchive.isBlank()) {
                ExtensionAbiVerifier.Report localReport = verifier.inspect(Path.of(abiArchive));
                if (!localReport.compatible()) {
                    throw new IllegalStateException("Local extension ABI gaps: " + localReport);
                }
            }

            String installed = System.getenv("ANILIB_DESKTOP_EXTENSION_ARCHIVE");
            if (installed != null && !installed.isBlank()) {
                Path installedArchive = Path.of(installed);
                ExtensionAbiVerifier.Report installedReport = verifier.inspect(installedArchive);
                if (!installedReport.compatible()) {
                    throw new IllegalStateException("Installed extension ABI gaps: " + installedReport);
                }
                verifyInstalledRuntime(directory, installedArchive);
            }
        } finally {
            try (var paths = Files.walk(directory)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(path);
                }
            }
        }
    }

    private static void verifyInstalledRuntime(Path directory, Path archive) {
        String archiveName = archive.getFileName().toString();
        if (!archiveName.endsWith(".jar")) {
            throw new IllegalArgumentException("Installed extension archive must use the .jar suffix");
        }
        Path apk = archive.resolveSibling(archiveName.substring(0, archiveName.length() - 4) + ".apk");
        ExtensionRegistry registry = new ExtensionRegistry(directory.resolve("installed-runtime"));
        var installed = registry.install(apk);
        try (ExtensionRuntimeCatalog catalog = new ExtensionRuntimeCatalog(registry);
                ExtensionRuntimeCatalog.Snapshot snapshot = catalog.discover()) {
            var source = snapshot.sources().stream()
                    .filter(candidate -> candidate.packageName().equals(installed.metadata().packageName()))
                    .findFirst();
            if (!snapshot.failures().isEmpty() || source.isEmpty()) {
                throw new IllegalStateException("Installed extension runtime failed: " + snapshot.failures());
            }
            try (ExtensionSourceOperations operations = new ExtensionSourceOperations(catalog)) {
                var page = operations.mangaCatalogue(source.orElseThrow().id(), "popular", 1, "");
                if (page.getMangas().isEmpty()) {
                    throw new IllegalStateException("Installed extension catalogue returned no manga");
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

    private static void writeCombinedUpdateSurfaceArchive(Path archive) throws IOException {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "sample/CombinedUpdateAbiConsumer",
                null, "java/lang/Object", null);
        MethodVisitor method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "invoke", "()V", null, null);
        method.visitCode();
        method.visitTypeInsn(Opcodes.NEW, S_MANGA_UPDATE);
        method.visitInsn(Opcodes.DUP);
        method.visitInsn(Opcodes.ACONST_NULL);
        method.visitInsn(Opcodes.ACONST_NULL);
        method.visitMethodInsn(Opcodes.INVOKESPECIAL, S_MANGA_UPDATE, "<init>",
                "(L" + S_MANGA + ";Ljava/util/List;)V", false);
        method.visitVarInsn(Opcodes.ASTORE, 0);
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, S_MANGA_UPDATE, "getManga",
                "()L" + S_MANGA + ";", false);
        method.visitInsn(Opcodes.POP);
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, S_MANGA_UPDATE, "getChapters",
                "()Ljava/util/List;", false);
        method.visitInsn(Opcodes.POP);
        method.visitInsn(Opcodes.ACONST_NULL);
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, CONTEXT_WRAPPER, "getCacheDir",
                "()Ljava/io/File;", false);
        method.visitInsn(Opcodes.POP);
        method.visitInsn(Opcodes.ACONST_NULL);
        method.visitInsn(Opcodes.ACONST_NULL);
        method.visitInsn(Opcodes.ACONST_NULL);
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, HTTP_SOURCE, "setUrlWithoutDomain",
                "(L" + S_CHAPTER + ";Ljava/lang/String;)V", false);
        method.visitInsn(Opcodes.ACONST_NULL);
        method.visitInsn(Opcodes.ACONST_NULL);
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, HTTP_SOURCE, "prepareNewChapter",
                "(L" + S_CHAPTER + ";)V", false);
        method.visitInsn(Opcodes.RETURN);
        method.visitMaxs(0, 0);
        method.visitEnd();
        writer.visitEnd();
        try (JarOutputStream jar = new JarOutputStream(Files.newOutputStream(archive))) {
            jar.putNextEntry(new JarEntry("sample/CombinedUpdateAbiConsumer.class"));
            jar.write(writer.toByteArray());
            jar.closeEntry();
        }
    }

    private static void writeAnimeSamaWebViewSurfaceArchive(Path archive) throws IOException {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "sample/AnimeSamaWebViewAbiConsumer",
                null, "java/lang/Object", null);
        MethodVisitor method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "invoke", "()V", null, null);
        method.visitCode();
        method.visitMethodInsn(Opcodes.INVOKESTATIC, LOOPER, "getMainLooper", "()L" + LOOPER + ";", false);
        method.visitTypeInsn(Opcodes.NEW, HANDLER);
        method.visitInsn(Opcodes.DUP_X1);
        method.visitInsn(Opcodes.SWAP);
        method.visitMethodInsn(Opcodes.INVOKESPECIAL, HANDLER, "<init>", "(L" + LOOPER + ";)V", false);
        method.visitVarInsn(Opcodes.ASTORE, 2);
        method.visitVarInsn(Opcodes.ALOAD, 2);
        method.visitInsn(Opcodes.ACONST_NULL);
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, HANDLER, "post", "(Ljava/lang/Runnable;)Z", false);
        method.visitInsn(Opcodes.POP);
        method.visitVarInsn(Opcodes.ALOAD, 2);
        method.visitInsn(Opcodes.ACONST_NULL);
        method.visitInsn(Opcodes.LCONST_1);
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, HANDLER, "postDelayed",
                "(Ljava/lang/Runnable;J)Z", false);
        method.visitInsn(Opcodes.POP);
        method.visitTypeInsn(Opcodes.NEW, WEB_VIEW);
        method.visitInsn(Opcodes.DUP);
        method.visitInsn(Opcodes.ACONST_NULL);
        method.visitMethodInsn(Opcodes.INVOKESPECIAL, WEB_VIEW, "<init>",
                "(Lfr/vriege/anilib/platform/desktopextensionhost/compat/android/content/Context;)V", false);
        method.visitVarInsn(Opcodes.ASTORE, 0);
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, WEB_VIEW, "getSettings", "()L" + WEB_SETTINGS + ";", false);
        method.visitVarInsn(Opcodes.ASTORE, 1);
        for (String setter : List.of("setJavaScriptEnabled", "setDomStorageEnabled", "setDatabaseEnabled",
                "setUseWideViewPort", "setLoadWithOverviewMode")) {
            method.visitVarInsn(Opcodes.ALOAD, 1);
            method.visitInsn(Opcodes.ICONST_1);
            method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, WEB_SETTINGS, setter, "(Z)V", false);
        }
        method.visitVarInsn(Opcodes.ALOAD, 1);
        method.visitLdcInsn("Anime-Sama");
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, WEB_SETTINGS,
                "setUserAgentString", "(Ljava/lang/String;)V", false);
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitTypeInsn(Opcodes.NEW, WEB_VIEW_CLIENT);
        method.visitInsn(Opcodes.DUP);
        method.visitMethodInsn(Opcodes.INVOKESPECIAL, WEB_VIEW_CLIENT, "<init>", "()V", false);
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, WEB_VIEW, "setWebViewClient",
                "(L" + WEB_VIEW_CLIENT + ";)V", false);
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitLdcInsn("https://example.test/video.m3u8");
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, WEB_VIEW, "loadUrl",
                "(Ljava/lang/String;)V", false);
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitLdcInsn("https://example.test/video.m3u8");
        method.visitInsn(Opcodes.ACONST_NULL);
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, WEB_VIEW, "loadUrl",
                "(Ljava/lang/String;Ljava/util/Map;)V", false);
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, WEB_VIEW, "stopLoading", "()V", false);
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, WEB_VIEW, "destroy", "()V", false);
        method.visitInsn(Opcodes.RETURN);
        method.visitMaxs(0, 0);
        method.visitEnd();
        writer.visitEnd();
        try (JarOutputStream jar = new JarOutputStream(Files.newOutputStream(archive))) {
            jar.putNextEntry(new JarEntry("sample/AnimeSamaWebViewAbiConsumer.class"));
            jar.write(writer.toByteArray());
            jar.closeEntry();
        }
    }

    private static void verifyWebViewSurfaceBehavior() {
        CountDownLatch posted = new CountDownLatch(1);
        Handler handler = new Handler(Looper.getMainLooper());
        if (!handler.post(posted::countDown) || !await(posted)) {
            throw new IllegalStateException("Desktop Handler did not execute its posted action");
        }
        CountDownLatch requestObserved = new CountDownLatch(1);
        AtomicReference<String> intercepted = new AtomicReference<>();
        WebView view = new WebView(Application.create());
        view.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView source, WebResourceRequest request) {
                intercepted.set(request.getUrl().toString());
                requestObserved.countDown();
                return null;
            }
        });
        view.loadUrl("https://example.test/video.m3u8", Map.of());
        if (!await(requestObserved) || !"https://example.test/video.m3u8".equals(intercepted.get())) {
            throw new IllegalStateException("Desktop WebView did not expose the media request");
        }
        view.destroy();
    }

    private static boolean await(CountDownLatch latch) {
        try {
            return latch.await(1L, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
