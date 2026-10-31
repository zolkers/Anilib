package fr.vriege.anilib.platform.desktopextensionhost.compat.aniyomi.util;

import kotlin.jvm.functions.Function1;
import kotlin.jvm.internal.Intrinsics;

import okhttp3.Response;

import java.io.IOException;
import java.io.UncheckedIOException;

/* Reified Kotlin functions are inlined into extension bytecode; reflective invocation has no type token. */
public final class JsonExtensionsKt {
    private JsonExtensionsKt() {}

    public static <T> T parseAs(String value) {
        Intrinsics.reifiedOperationMarker(4, "T");
        throw new IllegalStateException(
                "Reified parseAs must be inlined by the extension compiler");
    }

    public static <T> T parseAs(String value, Function1<? super String, String> transform) {
        return parseAs(transform.invoke(value));
    }

    public static <T> T parseAs(Response value) {
        try {
            return parseAs(value.body().string());
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    public static <T> T parseAs(Response value, Function1<? super String, String> transform) {
        try {
            return parseAs(value.body().string(), transform);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
