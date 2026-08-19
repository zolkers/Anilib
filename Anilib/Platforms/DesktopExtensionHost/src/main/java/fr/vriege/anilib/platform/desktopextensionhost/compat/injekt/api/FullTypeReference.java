package fr.vriege.anilib.platform.desktopextensionhost.compat.injekt.api;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

public abstract class FullTypeReference<T> {
    private final Type type;

    protected FullTypeReference() {
        Type genericParent = getClass().getGenericSuperclass();
        if (genericParent instanceof ParameterizedType parameterized) {
            type = parameterized.getActualTypeArguments()[0];
        } else {
            type = Object.class;
        }
    }

    public final Type getType() {
        return type;
    }
}
