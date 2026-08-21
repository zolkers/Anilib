package fr.vriege.anilib.platform.desktopextensionhost.compat.aniyomi.network;

import okhttp3.Interceptor;
import okhttp3.Response;

import java.io.IOException;

public final class CloudflareInterceptor implements Interceptor {
    public CloudflareInterceptor() {
    }

    @Override
    public Response intercept(Chain chain) throws IOException {
        return chain.proceed(chain.request());
    }
}
