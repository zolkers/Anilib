package fr.vriege.anilib.platform.desktopextensionhost.compat.aniyomi.network;

import okhttp3.Interceptor;
import okhttp3.Response;

import java.io.IOException;

public final class UncaughtExceptionInterceptor implements Interceptor {
    public UncaughtExceptionInterceptor() {
    }

    @Override
    public Response intercept(Chain chain) throws IOException {
        try {
            return chain.proceed(chain.request());
        } catch (IOException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new IOException("Unexpected failure while executing a source request", exception);
        }
    }
}
