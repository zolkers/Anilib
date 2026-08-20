package fr.vriege.anilib.feature.network.bundle;

import fr.vriege.anilib.feature.network.NetworkDiagnostic;
import fr.vriege.anilib.feature.network.NetworkMaintenance;
import fr.vriege.anilib.feature.network.NetworkPolicy;
import fr.vriege.anilib.framework.http.AnilibHttpClient;
import fr.vriege.anilib.framework.http.HttpRequest;
import fr.vriege.anilib.framework.http.HttpResponse;
import fr.vriege.anilib.framework.http.HttpCookieJar;
import fr.vriege.anilib.framework.http.HttpResponseCache;
import fr.vriege.anilib.foundation.validation.Preconditions;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Objects;

final class DefaultNetworkMaintenance implements NetworkMaintenance {
    private static final int MAX_DIAGNOSTICS = 50;

    private final HttpCookieJar cookies;
    private final HttpResponseCache cache;
    private final NetworkPolicyStore policyStore;
    private final ArrayDeque<NetworkDiagnostic> diagnostics = new ArrayDeque<>();
    private NetworkPolicy policy;
    private AnilibHttpClient client;

    DefaultNetworkMaintenance(
            HttpCookieJar cookies,
            HttpResponseCache cache,
            NetworkPolicyStore policyStore) {
        this.cookies = Preconditions.requireNonNull(cookies, "cookies");
        this.cache = Preconditions.requireNonNull(cache, "cache");
        this.policyStore = Preconditions.requireNonNull(policyStore, "policyStore");
        policy = policyStore.load();
    }

    synchronized void attach(AnilibHttpClient client) {
        if (this.client != null) {
            throw new IllegalStateException("Network client is already attached");
        }
        this.client = Preconditions.requireNonNull(client, "client");
    }

    @Override
    public synchronized NetworkPolicy policy() {
        return policy;
    }

    @Override
    public synchronized void savePolicy(NetworkPolicy policy) {
        NetworkPolicy checked = Preconditions.requireNonNull(policy, "policy");
        policyStore.save(checked);
        this.policy = checked;
    }

    @Override
    public NetworkDiagnostic diagnose(String sourceId, URI endpoint) {
        AnilibHttpClient active;
        NetworkPolicy activePolicy;
        synchronized (this) {
            active = client;
            activePolicy = policy;
        }
        if (active == null) {
            throw new IllegalStateException("Network client is not attached");
        }
        Instant checkedAt = Instant.now();
        long started = System.nanoTime();
        NetworkDiagnostic diagnostic;
        try {
            HttpResponse response = active.execute(HttpRequest.builder(endpoint)
                    .timeout(activePolicy.timeout())
                    .build());
            diagnostic = new NetworkDiagnostic(
                    sourceId,
                    endpoint,
                    checkedAt,
                    elapsed(started),
                    response.statusCode(),
                    response.fromCache(),
                    response.statusCode() >= 200 && response.statusCode() < 400,
                    "HTTP " + response.statusCode());
        } catch (RuntimeException failure) {
            diagnostic = new NetworkDiagnostic(
                    sourceId,
                    endpoint,
                    checkedAt,
                    elapsed(started),
                    0,
                    false,
                    false,
                    boundedMessage(failure));
        }
        synchronized (this) {
            diagnostics.addFirst(diagnostic);
            while (diagnostics.size() > MAX_DIAGNOSTICS) {
                diagnostics.removeLast();
            }
        }
        return diagnostic;
    }

    @Override
    public synchronized List<NetworkDiagnostic> diagnostics() {
        return List.copyOf(diagnostics);
    }

    @Override
    public void clearCookies() {
        cookies.clear();
    }

    @Override
    public void clearResponseCache() {
        cache.clear();
    }

    private static Duration elapsed(long started) {
        return Duration.ofNanos(Math.max(0, System.nanoTime() - started));
    }

    private static String boundedMessage(RuntimeException failure) {
        String message = Objects.toString(failure.getMessage(), failure.getClass().getSimpleName());
        return message.length() <= 512 ? message : message.substring(0, 512);
    }
}
