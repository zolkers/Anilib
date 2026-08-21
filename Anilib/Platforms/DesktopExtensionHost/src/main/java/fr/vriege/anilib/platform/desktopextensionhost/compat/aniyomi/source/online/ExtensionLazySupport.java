package fr.vriege.anilib.platform.desktopextensionhost.compat.aniyomi.source.online;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.util.Objects;

public final class ExtensionLazySupport {
    private ExtensionLazySupport() {
    }

    public static Object baseUrlHostInitializer(Object source) {
        Objects.requireNonNull(source, "source");
        try {
            Class<?> function = Class.forName(
                    "kotlin.jvm.functions.Function0", false, source.getClass().getClassLoader());
            return Proxy.newProxyInstance(source.getClass().getClassLoader(), new Class<?>[]{function},
                    (proxy, method, arguments) -> switch (method.getName()) {
                        case "invoke" -> baseUrlHost(source);
                        case "toString" -> "Anilib base URL host initializer";
                        case "hashCode" -> System.identityHashCode(proxy);
                        case "equals" -> arguments != null && arguments.length == 1 && proxy == arguments[0];
                        default -> throw new UnsupportedOperationException(
                                "Unsupported lazy initializer method: " + method.getName());
                    });
        } catch (ClassNotFoundException exception) {
            throw new IllegalStateException("Kotlin Function0 is unavailable", exception);
        }
    }

    private static String baseUrlHost(Object source) {
        try {
            Object value = source.getClass().getMethod("getBaseUrl").invoke(source);
            URI location = URI.create(Objects.requireNonNull(value, "baseUrl").toString());
            return Objects.requireNonNull(location.getHost(), "baseUrl host");
        } catch (NoSuchMethodException | IllegalAccessException exception) {
            throw new IllegalStateException("Extension base URL is unavailable", exception);
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new IllegalStateException("Extension base URL failed", cause);
        }
    }
}
