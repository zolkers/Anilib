package fr.vriege.anilib.platform.desktopextensionhost.compat.aniyomi.network;

import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;
import java.util.Objects;

public final class UserAgentInterceptor implements Interceptor {
    private final String userAgent;

    public UserAgentInterceptor(String userAgent) {
        this.userAgent = Objects.requireNonNull(userAgent, "userAgent");
    }

    @Override
    public Response intercept(Chain chain) throws IOException {
        Request request = chain.request();
        if (request.header("User-Agent") == null) {
            request = request.newBuilder().header("User-Agent", userAgent).build();
        }
        return chain.proceed(request);
    }
}
