package fr.vriege.anilib.feature.extensionrepository.runtime;

import fr.vriege.anilib.feature.extensionrepository.ExtensionPackageMetadata;
import fr.vriege.anilib.feature.extensionrepository.ExtensionRepositoryService;
import fr.vriege.anilib.feature.extensionrepository.ExtensionRepositorySnapshot;
import fr.vriege.anilib.framework.http.AnilibHttpClient;
import fr.vriege.anilib.framework.http.HttpCachePolicy;
import fr.vriege.anilib.framework.http.HttpRequest;
import fr.vriege.anilib.framework.http.HttpResponse;
import fr.vriege.anilib.foundation.validation.Preconditions;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Bounded HTTPS implementation of Aniyomi-compatible bring-your-own repositories. */
public final class DefaultExtensionRepositoryService implements ExtensionRepositoryService {
    private static final int MAX_REDIRECTS = 5;
    private static final int MAX_INDEX_BYTES = 4 * 1024 * 1024;
    private static final Duration CACHE_TTL = Duration.ofHours(6);
    private static final Duration MINIMUM_INTERVAL = Duration.ofMillis(250);

    private final FileExtensionRepositoryStore store;
    private final AnilibHttpClient client;
    private final Clock clock;
    private final AniyomiRepositoryIndexParser parser;
    private final List<URI> repositories;
    private final Map<URI, ExtensionRepositorySnapshot> snapshots = new LinkedHashMap<>();

    public DefaultExtensionRepositoryService(
            FileExtensionRepositoryStore store,
            AnilibHttpClient client) {
        this(store, client, Clock.systemUTC(), new AniyomiRepositoryIndexParser());
    }

    public DefaultExtensionRepositoryService(
            FileExtensionRepositoryStore store,
            AnilibHttpClient client,
            Clock clock,
            AniyomiRepositoryIndexParser parser) {
        this.store = Preconditions.requireNonNull(store, "store");
        this.client = Preconditions.requireNonNull(client, "client");
        this.clock = Preconditions.requireNonNull(clock, "clock");
        this.parser = Preconditions.requireNonNull(parser, "parser");
        repositories = new ArrayList<>(store.load());
    }

    @Override
    public synchronized List<URI> repositories() {
        return List.copyOf(repositories);
    }

    @Override
    public synchronized void add(URI indexUri) {
        URI repository = AniyomiRepositoryIndexParser.requireRepositoryUri(indexUri);
        if (!repositories.contains(repository)) {
            repositories.add(repository);
            store.save(repositories);
        }
    }

    @Override
    public synchronized boolean remove(URI indexUri) {
        URI repository = AniyomiRepositoryIndexParser.requireRepositoryUri(indexUri);
        if (!repositories.remove(repository)) {
            return false;
        }
        snapshots.remove(repository);
        store.save(repositories);
        return true;
    }

    @Override
    public synchronized List<ExtensionRepositorySnapshot> snapshots() {
        return repositories.stream().map(snapshots::get).filter(Objects::nonNull).toList();
    }

    @Override
    public synchronized ExtensionRepositorySnapshot refresh(URI indexUri) {
        URI repository = AniyomiRepositoryIndexParser.requireRepositoryUri(indexUri);
        if (!repositories.contains(repository)) {
            throw new IllegalArgumentException("Repository URL is not configured: " + repository);
        }
        Instant fetchedAt = clock.instant();
        ExtensionRepositorySnapshot snapshot;
        try {
            FetchResult fetched = fetchRepository(repository);
            HttpResponse response = fetched.response();
            if (response.body().length > MAX_INDEX_BYTES) {
                throw new IllegalArgumentException("Repository response exceeds 4 MiB");
            }
            List<ExtensionPackageMetadata> packages = parser.parse(fetched.finalUri(), response.bodyAsUtf8());
            snapshot = new ExtensionRepositorySnapshot(repository, fetchedAt, packages, java.util.Optional.empty());
        } catch (RuntimeException exception) {
            snapshot = new ExtensionRepositorySnapshot(
                    repository,
                    fetchedAt,
                    List.of(),
                    java.util.Optional.of(failureMessage(exception)));
        }
        snapshots.put(repository, snapshot);
        return snapshot;
    }

    @Override
    public synchronized List<ExtensionRepositorySnapshot> refreshAll() {
        return List.copyOf(repositories).stream().map(this::refresh).toList();
    }

    @Override
    public synchronized List<ExtensionPackageMetadata> packages() {
        Map<String, ExtensionPackageMetadata> latest = new LinkedHashMap<>();
        for (URI repository : repositories) {
            ExtensionRepositorySnapshot snapshot = snapshots.get(repository);
            if (snapshot == null || !snapshot.successful()) {
                continue;
            }
            for (ExtensionPackageMetadata extensionPackage : snapshot.packages()) {
                latest.merge(
                        extensionPackage.packageName(),
                        extensionPackage,
                        (left, right) -> right.versionCode() > left.versionCode() ? right : left);
            }
        }
        return latest.values().stream()
                .sorted(Comparator.comparing(ExtensionPackageMetadata::displayName)
                        .thenComparing(ExtensionPackageMetadata::packageName))
                .toList();
    }

    private FetchResult fetch(URI initialUri) {
        URI current = initialUri;
        for (int redirects = 0; redirects <= MAX_REDIRECTS; redirects++) {
            HttpRequest request = HttpRequest.builder(current)
                    .header("accept", "application/json")
                    .cache(HttpCachePolicy.refresh(CACHE_TTL))
                    .minimumInterval(MINIMUM_INTERVAL)
                    .build();
            HttpResponse response = client.execute(request);
            if (!redirect(response.statusCode())) {
                if (response.statusCode() != 200) {
                    throw new IllegalStateException("Repository returned HTTP " + response.statusCode());
                }
                return new FetchResult(current, response);
            }
            if (redirects == MAX_REDIRECTS) {
                throw new IllegalStateException("Repository exceeded " + MAX_REDIRECTS + " redirects");
            }
            String location = response.firstHeader("location")
                    .orElseThrow(() -> new IllegalStateException("Repository redirect has no Location header"));
            current = AniyomiRepositoryIndexParser.requireRepositoryUri(current.resolve(location));
        }
        throw new IllegalStateException("Unreachable repository redirect state");
    }

    private FetchResult fetchRepository(URI configuredLocation) {
        RuntimeException lastFailure = null;
        for (URI candidate : ExtensionRepositoryLocations.indexCandidates(configuredLocation)) {
            try {
                return fetch(candidate);
            } catch (RuntimeException exception) {
                if (lastFailure != null) {
                    exception.addSuppressed(lastFailure);
                }
                lastFailure = exception;
            }
        }
        throw Objects.requireNonNull(lastFailure, "repository candidates");
    }

    private boolean redirect(int statusCode) {
        return statusCode == 301
                || statusCode == 302
                || statusCode == 303
                || statusCode == 307
                || statusCode == 308;
    }

    private String failureMessage(RuntimeException exception) {
        String message = exception.getMessage();
        String value = exception.getClass().getSimpleName()
                + (message == null || message.isBlank() ? "" : ": " + message);
        return value.length() <= 240 ? value : value.substring(0, 240);
    }

    private record FetchResult(URI finalUri, HttpResponse response) {
    }
}
