package fr.vriege.anilib.platform.desktopextensionhost.compat.aniyomi.network;

import java.io.IOException;
import java.io.UncheckedIOException;
import kotlin.coroutines.Continuation;
import okhttp3.Call;
import okhttp3.Response;

public final class OkHttpExtensionsKt {
    private OkHttpExtensionsKt() {
    }

    public static Object await(Call call, Continuation<? super Response> continuation) {
        return execute(call, false);
    }

    public static Object awaitSuccess(Call call, Continuation<? super Response> continuation) {
        return execute(call, true);
    }

    private static Response execute(Call call, boolean requireSuccess) {
        try {
            Response response = call.execute();
            if (requireSuccess && !response.isSuccessful()) {
                int status = response.code();
                response.close();
                throw new IllegalStateException("Source request failed with HTTP " + status);
            }
            return response;
        } catch (IOException exception) {
            throw new UncheckedIOException("Source request failed", exception);
        }
    }
}
