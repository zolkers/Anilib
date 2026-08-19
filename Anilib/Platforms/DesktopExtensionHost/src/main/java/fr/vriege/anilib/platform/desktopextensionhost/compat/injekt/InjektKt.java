package fr.vriege.anilib.platform.desktopextensionhost.compat.injekt;

import fr.vriege.anilib.platform.desktopextensionhost.compat.android.app.Application;
import fr.vriege.anilib.platform.desktopextensionhost.compat.aniyomi.network.NetworkHelper;
import fr.vriege.anilib.platform.desktopextensionhost.compat.injekt.api.InjektScope;
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
                return Class.forName("kotlinx.serialization.json.Json").getField("Default").get(null);
            } catch (ReflectiveOperationException error) {
                throw new IllegalStateException("Kotlin JSON runtime is unavailable", error);
            }
        }
    }
}
