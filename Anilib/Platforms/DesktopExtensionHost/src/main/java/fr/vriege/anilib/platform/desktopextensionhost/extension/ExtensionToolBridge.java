package fr.vriege.anilib.platform.desktopextensionhost.extension;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Path;

final class ExtensionToolBridge {
    private ExtensionToolBridge() {
    }

    static RawApkMetadata readMetadata(Path apk) {
        try {
            Class<?> apkFileType = Class.forName("net.dongliu.apk.parser.ApkFile");
            Constructor<?> constructor = apkFileType.getConstructor(File.class);
            Object parsed = constructor.newInstance(apk.toFile());
            try (Closeable parsedResource = (Closeable) parsed) {
                Object metadata = apkFileType.getMethod("getApkMeta").invoke(parsedResource);
                String manifest = (String) apkFileType.getMethod("getManifestXml").invoke(parsedResource);
                Class<?> metadataType = metadata.getClass();
                String packageName = (String) metadataType.getMethod("getPackageName").invoke(metadata);
                String versionName = (String) metadataType.getMethod("getVersionName").invoke(metadata);
                Long versionCode = (Long) metadataType.getMethod("getVersionCode").invoke(metadata);
                return new RawApkMetadata(
                        packageName,
                        versionName,
                        versionCode == null ? 0 : versionCode,
                        manifest);
            }
        } catch (InvocationTargetException exception) {
            throw toolFailure("Unable to read extension APK", exception.getCause());
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("APK parser API is unavailable or incompatible", exception);
        } catch (IOException exception) {
            throw new UncheckedIOException("Unable to close extension APK", exception);
        }
    }

    static void convertDex(Path apk, Path destination) {
        try {
            Class<?> dex2jarType = Class.forName("com.googlecode.d2j.dex.Dex2jar");
            Object converter = dex2jarType.getMethod("from", File.class).invoke(null, apk.toFile());
            converter = invoke(converter, "reUseReg", boolean.class, false);
            converter = invoke(converter, "topoLogicalSort");
            converter = invoke(converter, "skipDebug", boolean.class, true);
            converter = invoke(converter, "optimizeSynchronized", boolean.class, false);
            converter = invoke(converter, "printIR", boolean.class, false);
            converter = invoke(converter, "noCode", boolean.class, false);
            converter = invoke(converter, "skipExceptions", boolean.class, false);
            converter = invoke(converter, "dontSanitizeNames", boolean.class, true);
            converter.getClass().getMethod("to", Path.class).invoke(converter, destination);
        } catch (InvocationTargetException exception) {
            throw toolFailure("Unable to convert extension DEX bytecode", exception.getCause());
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("DEX converter API is unavailable or incompatible", exception);
        }
    }

    private static Object invoke(Object target, String name) throws ReflectiveOperationException {
        return target.getClass().getMethod(name).invoke(target);
    }

    private static Object invoke(Object target, String name, Class<?> parameter, Object value)
            throws ReflectiveOperationException {
        return target.getClass().getMethod(name, parameter).invoke(target, value);
    }

    private static RuntimeException toolFailure(String message, Throwable cause) {
        if (cause instanceof IOException exception) {
            return new UncheckedIOException(message, exception);
        }
        if (cause instanceof RuntimeException exception) {
            return exception;
        }
        return new IllegalStateException(message, cause);
    }

    record RawApkMetadata(String packageName, String versionName, long versionCode, String manifestXml) {
    }
}
