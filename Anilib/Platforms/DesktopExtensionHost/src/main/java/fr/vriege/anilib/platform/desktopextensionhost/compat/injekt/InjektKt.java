package fr.vriege.anilib.platform.desktopextensionhost.compat.injekt;

import fr.vriege.anilib.platform.desktopextensionhost.compat.android.app.Application;
import fr.vriege.anilib.platform.desktopextensionhost.compat.aniyomi.network.NetworkHelper;
import fr.vriege.anilib.platform.desktopextensionhost.compat.injekt.api.InjektScope;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.lang.reflect.Type;
import okhttp3.OkHttpClient;

public final class InjektKt {
    private static final InjektScope SCOPE = new HostScope();

    private InjektKt() {
    }

    public static InjektScope getInjekt() {
        return SCOPE;
    }

    private static final class HostScope implements InjektScope {
        @Override
        public Object getInstance(Type type) {
            String name = type.getTypeName();
            if (name.equals(Application.class.getName())) {
                return Application.create();
            }
            if (name.equals("kotlinx.serialization.json.Json")) {
                return jsonDefault();
            }
            if (name.equals(NetworkHelper.class.getName())) {
                return NetworkHelper.shared();
            }
            if (name.equals(OkHttpClient.class.getName())) {
                return NetworkHelper.shared().getClient();
            }
            throw new IllegalArgumentException("No Anilib host dependency for " + name);
        }

        private static Object jsonDefault() {
            try {
                Class<?> jsonType = Class.forName("kotlinx.serialization.json.Json");
                Class<?> functionType = Class.forName("kotlin.jvm.functions.Function1");
                Object defaults = jsonType.getField("Default").get(null);
                InvocationHandler configure = (proxy, method, arguments) -> {
                    if (method.getDeclaringClass().equals(Object.class)) {
                        return switch (method.getName()) {
                            case "toString" -> "AnilibJsonConfiguration";
                            case "hashCode" -> System.identityHashCode(proxy);
                            case "equals" -> proxy == arguments[0];
                            default -> throw new UnsupportedOperationException(method.getName());
                        };
                    }
                    Object builder = arguments[0];
                    builder.getClass()
                            .getMethod("setIgnoreUnknownKeys", boolean.class)
                            .invoke(builder, true);
                    return Class.forName("kotlin.Unit").getField("INSTANCE").get(null);
                };
                Object action = Proxy.newProxyInstance(
                        InjektKt.class.getClassLoader(), new Class<?>[] {functionType}, configure);
                return Class.forName("kotlinx.serialization.json.JsonKt")
                        .getMethod("Json", jsonType, functionType)
                        .invoke(null, defaults, action);
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("Could not configure the extension JSON runtime", exception);
            }
        }
    }
}
