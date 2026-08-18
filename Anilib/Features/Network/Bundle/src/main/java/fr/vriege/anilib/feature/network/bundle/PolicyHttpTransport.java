package fr.vriege.anilib.feature.network.bundle;

import fr.vriege.anilib.feature.network.NetworkPolicy;
import fr.vriege.anilib.framework.http.HttpException;
import fr.vriege.anilib.framework.http.HttpMethod;
import fr.vriege.anilib.framework.http.HttpRequest;
import fr.vriege.anilib.framework.http.HttpResponse;
import fr.vriege.anilib.framework.http.HttpTransport;
import fr.vriege.anilib.framework.http.runtime.UrlConnectionHttpTransport;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

final class PolicyHttpTransport implements HttpTransport {
    private static final Duration DNS_VALIDATION_TTL = Duration.ofMinutes(10);

    private final HttpTransport direct;
    private final Supplier<NetworkPolicy> policy;
    private final UrlConnectionHttpTransport proxied;
    private final Map<String, Instant> dnsValidatedUntil = new HashMap<>();

    PolicyHttpTransport(HttpTransport direct, Supplier<NetworkPolicy> policy) {
        this.direct = Objects.requireNonNull(direct, "direct must not be null");
        this.policy = Objects.requireNonNull(policy, "policy must not be null");
        proxied = new UrlConnectionHttpTransport(() -> this.policy.get().proxy());
    }

    @Override
    public HttpResponse exchange(HttpRequest request, Map<String, List<String>> headers) {
        NetworkPolicy active = policy.get();
        active.dnsOverHttps().ifPresent(endpoint -> validateDns(endpoint, request.uri().getHost(), active));
        HttpRequest configured = withTimeout(request, active.timeout());
        return active.proxy().isPresent()
                ? proxied.exchange(configured, headers)
                : direct.exchange(configured, headers);
    }

    private synchronized void validateDns(URI endpoint, String host, NetworkPolicy active) {
        Instant now = Instant.now();
        if (dnsValidatedUntil.getOrDefault(host, Instant.EPOCH).isAfter(now)) {
            return;
        }
        byte[] query = dnsQuery(host);
        HttpRequest request = HttpRequest.builder(endpoint)
                .method(HttpMethod.POST)
                .header("Accept", "application/dns-message")
                .header("Content-Type", "application/dns-message")
                .body(query)
                .timeout(active.timeout())
                .build();
        HttpResponse response = active.proxy().isPresent()
                ? proxied.exchange(request, request.headers())
                : new UrlConnectionHttpTransport().exchange(request, request.headers());
        byte[] body = response.body();
        if (response.statusCode() < 200 || response.statusCode() >= 300
                || body.length < 12 || (body[3] & 0x0F) != 0) {
            throw new HttpException("DNS-over-HTTPS resolution failed for " + host);
        }
        dnsValidatedUntil.put(host, now.plus(DNS_VALIDATION_TTL));
    }

    private static HttpRequest withTimeout(HttpRequest request, Duration timeout) {
        HttpRequest.Builder builder = HttpRequest.builder(request.uri())
                .method(request.method())
                .timeout(timeout)
                .cache(request.cachePolicy())
                .minimumInterval(request.minimumInterval());
        byte[] body = request.body();
        if (body.length > 0) {
            builder.body(body);
        }
        return builder.build();
    }

    private static byte[] dnsQuery(String host) {
        int identifier = ThreadLocalRandom.current().nextInt(0x10000);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        writeShort(output, identifier);
        writeShort(output, 0x0100);
        writeShort(output, 1);
        writeShort(output, 0);
        writeShort(output, 0);
        writeShort(output, 0);
        for (String label : host.split("\\.")) {
            byte[] bytes = label.getBytes(StandardCharsets.US_ASCII);
            if (bytes.length == 0 || bytes.length > 63) {
                throw new HttpException("Invalid DNS host label");
            }
            output.write(bytes.length);
            output.writeBytes(bytes);
        }
        output.write(0);
        writeShort(output, 1);
        writeShort(output, 1);
        return output.toByteArray();
    }

    private static void writeShort(ByteArrayOutputStream output, int value) {
        output.write((value >>> 8) & 0xFF);
        output.write(value & 0xFF);
    }
}
