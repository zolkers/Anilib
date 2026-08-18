package fr.vriege.anilib.feature.network.bundle;

import fr.vriege.anilib.feature.network.NetworkCapabilities;
import fr.vriege.anilib.framework.http.HttpCookieJar;
import fr.vriege.anilib.framework.http.HttpRateLimiter;
import fr.vriege.anilib.framework.http.HttpResponseCache;
import fr.vriege.anilib.framework.http.HttpTransport;
import fr.vriege.anilib.framework.http.runtime.DefaultAnilibHttpClient;
import fr.vriege.anilib.framework.http.runtime.FileHttpResponseCache;
import fr.vriege.anilib.framework.http.runtime.HostHttpRateLimiter;
import fr.vriege.anilib.framework.http.runtime.JdkHttpCookieJar;
import fr.vriege.anilib.framework.http.runtime.UrlConnectionHttpTransport;
import fr.vriege.anilib.foundation.component.ComponentDescriptor;
import fr.vriege.anilib.kernel.AnilibPlugin;
import fr.vriege.anilib.kernel.PluginInstallationContext;
import fr.vriege.anilib.kernel.PluginManifest;

import java.nio.file.Path;
import java.util.Objects;

public final class NetworkPlugin implements AnilibPlugin {
    private static final PluginManifest MANIFEST = PluginManifest.builder(
                    ComponentDescriptor.of("feature.network", "Network", "1.0.0"))
            .provides(NetworkCapabilities.HTTP_CLIENT)
            .provides(NetworkCapabilities.COOKIES)
            .provides(NetworkCapabilities.RATE_LIMITER)
            .provides(NetworkCapabilities.RESPONSE_CACHE)
            .provides(NetworkCapabilities.MAINTENANCE)
            .build();

    private final Path cacheDirectory;
    private final HttpTransport transport;

    public NetworkPlugin(Path cacheDirectory) {
        this(cacheDirectory, new UrlConnectionHttpTransport());
    }

    public NetworkPlugin(Path cacheDirectory, HttpTransport transport) {
        this.cacheDirectory = Objects.requireNonNull(cacheDirectory, "cacheDirectory must not be null")
                .toAbsolutePath()
                .normalize();
        this.transport = Objects.requireNonNull(transport, "transport must not be null");
    }

    @Override
    public PluginManifest manifest() {
        return MANIFEST;
    }

    @Override
    public void install(PluginInstallationContext context) {
        HttpCookieJar cookies = new JdkHttpCookieJar();
        HttpResponseCache cache = new FileHttpResponseCache(cacheDirectory);
        HttpRateLimiter rateLimiter = new HostHttpRateLimiter();
        context.publish(NetworkCapabilities.COOKIES, cookies);
        context.publish(NetworkCapabilities.RESPONSE_CACHE, cache);
        context.publish(NetworkCapabilities.RATE_LIMITER, rateLimiter);
        context.publish(NetworkCapabilities.MAINTENANCE, new DefaultNetworkMaintenance(cookies, cache));
        context.publish(
                NetworkCapabilities.HTTP_CLIENT,
                new DefaultAnilibHttpClient(transport, cookies, cache, rateLimiter));
    }
}
