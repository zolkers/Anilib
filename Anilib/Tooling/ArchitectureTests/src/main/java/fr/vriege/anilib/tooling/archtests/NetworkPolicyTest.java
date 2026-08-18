package fr.vriege.anilib.tooling.archtests;

import fr.vriege.anilib.configuration.standard.StandardAnilib;
import fr.vriege.anilib.feature.network.NetworkCapabilities;
import fr.vriege.anilib.feature.network.NetworkMaintenance;
import fr.vriege.anilib.feature.network.NetworkPolicy;
import fr.vriege.anilib.framework.http.HttpCachePolicy;
import fr.vriege.anilib.framework.http.HttpRequest;
import fr.vriege.anilib.framework.http.HttpResponse;
import fr.vriege.anilib.framework.http.HttpTransport;
import fr.vriege.anilib.kernel.StartedAnilib;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

final class NetworkPolicyTest {
    private NetworkPolicyTest() {
    }

    static int run() {
        Counter counter = new Counter();
        Path directory = temporaryDirectory();
        AtomicInteger exchanges = new AtomicInteger();
        AtomicReference<Map<String, List<String>>> headers = new AtomicReference<>();
        AtomicReference<Duration> timeout = new AtomicReference<>();
        HttpTransport transport = (request, sentHeaders) -> {
            exchanges.incrementAndGet();
            headers.set(sentHeaders);
            timeout.set(request.timeout());
            return new HttpResponse(204, Map.of(), new byte[0], false);
        };
        NetworkPolicy saved = new NetworkPolicy(
                "Anilib-Test/1.0",
                Optional.empty(),
                Optional.empty(),
                Duration.ofSeconds(12),
                false);
        try {
            try (StartedAnilib application = StandardAnilib.start(directory, transport, List.of())) {
                NetworkMaintenance maintenance = application.capability(NetworkCapabilities.MAINTENANCE);
                maintenance.savePolicy(saved);
                var diagnostic = maintenance.diagnose("test.source", URI.create("https://source.test/health"));
                counter.check(diagnostic.successful() && diagnostic.statusCode() == 204,
                        "per-source diagnostics must retain successful status and source identity");
                counter.check(maintenance.diagnostics().equals(List.of(diagnostic)),
                        "network diagnostics must expose their newest bounded history");
                counter.check(headers.get().get("user-agent").equals(List.of("Anilib-Test/1.0")),
                        "network policy must inject the configured default user agent");
                counter.check(timeout.get().equals(Duration.ofSeconds(12)),
                        "network policy must override transport timeouts");

                var client = application.capability(NetworkCapabilities.HTTP_CLIENT);
                HttpRequest cacheable = HttpRequest.builder(URI.create("https://source.test/cache"))
                        .cache(HttpCachePolicy.preferCache(Duration.ofMinutes(1)))
                        .build();
                client.execute(cacheable);
                client.execute(cacheable);
                counter.check(exchanges.get() == 3,
                        "disabled response caching must execute every cacheable request");
            }
            try (StartedAnilib reopened = StandardAnilib.start(directory, transport, List.of())) {
                counter.check(reopened.capability(NetworkCapabilities.MAINTENANCE).policy().equals(saved),
                        "network policy must survive a complete application restart");
            }
            return counter.value;
        } finally {
            deleteDirectory(directory);
        }
    }

    private static Path temporaryDirectory() {
        try {
            return Files.createTempDirectory("anilib-network-policy-test");
        } catch (IOException exception) {
            throw new AssertionError("Unable to create network policy test directory", exception);
        }
    }

    private static void deleteDirectory(Path directory) {
        try (java.util.stream.Stream<Path> entries = Files.walk(directory)) {
            for (Path entry : entries.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(entry);
            }
        } catch (IOException exception) {
            throw new AssertionError("Unable to clean network policy test directory", exception);
        }
    }

    private static final class Counter {
        private int value;

        private void check(boolean condition, String message) {
            value++;
            if (!condition) {
                throw new AssertionError(message);
            }
        }
    }
}
