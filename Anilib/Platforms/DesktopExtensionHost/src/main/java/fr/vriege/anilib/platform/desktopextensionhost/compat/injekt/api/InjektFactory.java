package fr.vriege.anilib.platform.desktopextensionhost.compat.injekt.api;

import java.lang.reflect.Type;

public interface InjektFactory {
    Object getInstance(Type type);

    default Object getInstanceOrNull(Type type) {
        try {
            return getInstance(type);
        } catch (IllegalArgumentException error) {
            return null;
        }
    }
}
