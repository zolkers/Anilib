package fr.vriege.anilib.feature.tracker.kitsu;

import fr.vriege.anilib.feature.library.LibraryItem;
import fr.vriege.anilib.feature.library.LibraryItemId;
import fr.vriege.anilib.feature.library.MediaKind;
import fr.vriege.anilib.feature.tracker.Tracker;
import fr.vriege.anilib.feature.tracker.TrackerAuthentication;
import fr.vriege.anilib.feature.tracker.TrackerCredentials;
import fr.vriege.anilib.feature.tracker.TrackerDescriptor;
import fr.vriege.anilib.feature.tracker.TrackerEntry;
import fr.vriege.anilib.feature.tracker.TrackerException;
import fr.vriege.anilib.feature.tracker.TrackerId;
import fr.vriege.anilib.feature.tracker.TrackerIcon;
import fr.vriege.anilib.feature.tracker.TrackerSdk;
import fr.vriege.anilib.feature.tracker.TrackerSearchResult;
import fr.vriege.anilib.feature.tracker.TrackerStatus;
import fr.vriege.anilib.feature.tracker.providersupport.TrackerJson;
import fr.vriege.anilib.framework.http.AnilibHttpClient;
import fr.vriege.anilib.framework.http.HttpMethod;
import fr.vriege.anilib.framework.http.HttpRequest;
import fr.vriege.anilib.framework.http.HttpResponse;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;

public final class KitsuTracker implements Tracker {
    private static final URI TOKEN_ENDPOINT = URI.create("https://kitsu.io/api/oauth/token");
    private static final URI API = URI.create("https://kitsu.io/api/edge/");
    private static final TrackerId ID = TrackerId.of("kitsu");
    private static final TrackerDescriptor DESCRIPTOR = new TrackerDescriptor(
            ID,
            "Kitsu",
            new TrackerIcon("K", 0xFD755C),
            TrackerSdk.API_VERSION,
            Set.of(MediaKind.ANIME, MediaKind.MANGA),
            TrackerAuthentication.USERNAME_PASSWORD,
            List.of(
                    TrackerStatus.WATCHING,
                    TrackerStatus.READING,
                    TrackerStatus.COMPLETED,
                    TrackerStatus.ON_HOLD,
                    TrackerStatus.PLANNING,
                    TrackerStatus.DROPPED,
                    TrackerStatus.REWATCHING,
                    TrackerStatus.REREADING),
            scores(),
            true,
            true);
    private final AnilibHttpClient client;
    private String token;
    private String userId = "";
    private String accountName = "";

    public KitsuTracker(AnilibHttpClient client) {
        this.client = Objects.requireNonNull(client, "client must not be null");
    }

    @Override
    public TrackerDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public boolean isAuthenticated() {
        return token != null;
    }

    @Override
    public String accountName() {
        return accountName;
    }

    @Override
    public void authenticate(TrackerCredentials credentials) {
        TrackerCredentials value = Objects.requireNonNull(credentials, "credentials must not be null");
        if (value.authentication() != TrackerAuthentication.USERNAME_PASSWORD) {
            throw new TrackerException("Kitsu requires a username and password");
        }
        String form = "grant_type=password&username=" + form(value.identity())
                + "&password=" + form(value.secret());
        HttpRequest request = HttpRequest.builder(TOKEN_ENDPOINT)
                .method(HttpMethod.POST)
                .header("content-type", "application/x-www-form-urlencoded")
                .header("accept", "application/json")
                .minimumInterval(Duration.ofMillis(250))
                .body(form.getBytes(StandardCharsets.UTF_8))
                .build();
        try {
            Map<String, Object> response = TrackerJson.object(
                    TrackerJson.execute(client, request), "Kitsu token response");
            token = TrackerJson.memberString(response, "access_token");
            List<Object> users = data(get("users?filter[self]=true"));
            if (users.size() != 1) {
                throw new TrackerException("Kitsu did not return the authenticated user");
            }
            Map<String, Object> user = TrackerJson.object(users.getFirst(), "Kitsu user");
            userId = TrackerJson.memberString(user, "id");
            accountName = TrackerJson.memberString(
                    TrackerJson.memberObject(user, "attributes"), "name");
        } catch (RuntimeException exception) {
            logout();
            throw exception;
        }
    }

    @Override
    public void logout() {
        token = null;
        userId = "";
        accountName = "";
    }

    @Override
    public List<TrackerSearchResult> search(String query, MediaKind kind) {
        requireAuthenticated();
        String value = Objects.requireNonNull(query, "query must not be null").strip();
        if (value.isEmpty()) {
            throw new TrackerException("Kitsu search query must not be blank");
        }
        String type = mediaType(kind);
        List<TrackerSearchResult> results = new ArrayList<>();
        for (Object child : data(get(type + "?filter[text]=" + form(value) + "&page[limit]=20"))) {
            Map<String, Object> media = TrackerJson.object(child, "Kitsu media");
            Map<String, Object> attributes = TrackerJson.memberObject(media, "attributes");
            String id = TrackerJson.memberString(media, "id");
            long total = optionalLong(attributes.get(kind == MediaKind.ANIME
                    ? "episodeCount"
                    : "chapterCount"));
            results.add(new TrackerSearchResult(
                    ID,
                    id,
                    title(attributes),
                    kind,
                    total,
                    Optional.of(URI.create("https://kitsu.io/" + type + "/" + id))));
        }
        return List.copyOf(results);
    }

    @Override
    public TrackerEntry bind(LibraryItem item, TrackerSearchResult result) {
        requireAuthenticated();
        LibraryItem libraryItem = Objects.requireNonNull(item, "item must not be null");
        TrackerSearchResult match = Objects.requireNonNull(result, "result must not be null");
        if (!match.trackerId().equals(ID) || match.kind() != libraryItem.kind()) {
            throw new TrackerException("Kitsu search result does not match this library title");
        }
        Map<String, Object> relationships = Map.of(
                "user", relationship("users", userId),
                "media", relationship(mediaType(match.kind()), match.remoteId()));
        Map<String, Object> body = Map.of("data", Map.of(
                "type", "libraryEntries",
                "attributes", Map.of("status", "planned"),
                "relationships", relationships));
        Map<String, Object> created = resource(post("library-entries", body));
        return entry(
                libraryItem.id(), created, match.title(), match.totalUnits(), match.remoteUri(), match.kind());
    }

    @Override
    public TrackerEntry update(TrackerEntry entry) {
        requireOwned(entry);
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("status", remoteStatus(entry.status()));
        attributes.put("progress", (long) Math.floor(entry.progress()));
        attributes.put("reconsuming", entry.status() == TrackerStatus.REWATCHING
                || entry.status() == TrackerStatus.REREADING);
        attributes.put("ratingTwenty", entry.score().isPresent()
                ? Math.round(entry.score().getAsDouble() * 2.0D)
                : null);
        attributes.put("startedAt", entry.startDate().map(LocalDate::toString).orElse(null));
        attributes.put("finishedAt", entry.finishDate().map(LocalDate::toString).orElse(null));
        attributes.put("private", entry.privateEntry());
        Map<String, Object> body = Map.of("data", Map.of(
                "type", "libraryEntries",
                "id", entry.remoteId(),
                "attributes", attributes));
        Map<String, Object> updated = resource(patch("library-entries/" + entry.remoteId(), body));
        return entry(
                entry.libraryItemId(), updated, entry.title(), entry.totalUnits(), entry.remoteUri(), kind(entry));
    }

    @Override
    public TrackerEntry refresh(TrackerEntry entry) {
        requireOwned(entry);
        Map<String, Object> refreshed = resource(get("library-entries/" + entry.remoteId()));
        return entry(
                entry.libraryItemId(), refreshed, entry.title(), entry.totalUnits(), entry.remoteUri(), kind(entry));
    }

    @Override
    public void remove(TrackerEntry entry) {
        requireOwned(entry);
        HttpRequest request = authenticated(API.resolve("library-entries/" + entry.remoteId()))
                .method(HttpMethod.DELETE)
                .build();
        HttpResponse response = client.execute(request);
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new TrackerException("Kitsu removal failed with HTTP " + response.statusCode());
        }
    }

    private Object get(String path) {
        requireAuthenticated();
        return TrackerJson.execute(client, authenticated(API.resolve(path)).build());
    }

    private Object post(String path, Map<String, Object> body) {
        return send(path, body, HttpMethod.POST);
    }

    private Object patch(String path, Map<String, Object> body) {
        return send(path, body, HttpMethod.PATCH);
    }

    private Object send(String path, Map<String, Object> body, HttpMethod method) {
        requireAuthenticated();
        HttpRequest request = authenticated(API.resolve(path))
                .method(method)
                .header("content-type", "application/vnd.api+json")
                .body(TrackerJson.encode(body).getBytes(StandardCharsets.UTF_8))
                .build();
        return TrackerJson.execute(client, request);
    }

    private HttpRequest.Builder authenticated(URI uri) {
        return HttpRequest.builder(uri)
                .header("accept", "application/vnd.api+json")
                .header("authorization", "Bearer " + token)
                .minimumInterval(Duration.ofMillis(250));
    }

    private static List<Object> data(Object document) {
        Map<String, Object> root = TrackerJson.object(document, "Kitsu response");
        return TrackerJson.memberArray(root, "data");
    }

    private static Map<String, Object> resource(Object document) {
        return TrackerJson.memberObject(
                TrackerJson.object(document, "Kitsu response"), "data");
    }

    private static TrackerEntry entry(
            LibraryItemId libraryItemId,
            Map<String, Object> resource,
            String title,
            long total,
            Optional<URI> remoteUri,
            MediaKind kind) {
        Map<String, Object> attributes = TrackerJson.memberObject(resource, "attributes");
        boolean reconsuming = TrackerJson.booleanValue(attributes.get("reconsuming"), false);
        TrackerStatus status = status(
                TrackerJson.memberString(attributes, "status"),
                reconsuming,
                kind);
        return new TrackerEntry(
                libraryItemId,
                ID,
                TrackerJson.memberString(resource, "id"),
                title,
                optionalDouble(attributes.get("progress"), 0.0D),
                total,
                status,
                rating(attributes.get("ratingTwenty")),
                date(attributes.get("startedAt")),
                date(attributes.get("finishedAt")),
                TrackerJson.booleanValue(attributes.get("private"), false),
                remoteUri,
                instant(attributes.get("updatedAt")));
    }

    private static Map<String, Object> relationship(String type, String id) {
        return Map.of("data", Map.of("type", type, "id", id));
    }

    private static String title(Map<String, Object> attributes) {
        return TrackerJson.optionalString(attributes.get("canonicalTitle"))
                .or(() -> TrackerJson.optionalString(attributes.get("slug")))
                .orElseThrow(() -> new TrackerException("Kitsu media has no title"));
    }

    private static String mediaType(MediaKind kind) {
        return switch (Objects.requireNonNull(kind, "kind must not be null")) {
            case ANIME -> "anime";
            case MANGA -> "manga";
            default -> throw new TrackerException("Kitsu supports only anime and manga");
        };
    }

    private static String remoteStatus(TrackerStatus status) {
        return switch (status) {
            case WATCHING, READING, REWATCHING, REREADING -> "current";
            case COMPLETED -> "completed";
            case ON_HOLD -> "on_hold";
            case PLANNING -> "planned";
            case DROPPED -> "dropped";
        };
    }

    private static TrackerStatus status(String value, boolean reconsuming, MediaKind kind) {
        return switch (value) {
            case "current" -> reconsuming
                    ? (kind == MediaKind.ANIME ? TrackerStatus.REWATCHING : TrackerStatus.REREADING)
                    : (kind == MediaKind.ANIME ? TrackerStatus.WATCHING : TrackerStatus.READING);
            case "completed" -> TrackerStatus.COMPLETED;
            case "on_hold" -> TrackerStatus.ON_HOLD;
            case "planned" -> TrackerStatus.PLANNING;
            case "dropped" -> TrackerStatus.DROPPED;
            default -> throw new TrackerException("Unsupported Kitsu status: " + value);
        };
    }

    private static MediaKind kind(TrackerEntry entry) {
        if (entry.remoteUri().map(URI::getPath).orElse("").contains("/manga/")) {
            return MediaKind.MANGA;
        }
        if (entry.status() == TrackerStatus.READING || entry.status() == TrackerStatus.REREADING) {
            return MediaKind.MANGA;
        }
        return MediaKind.ANIME;
    }

    private static OptionalDouble rating(Object value) {
        if (!(value instanceof Number number) || number.doubleValue() <= 0.0D) {
            return OptionalDouble.empty();
        }
        return OptionalDouble.of(number.doubleValue() / 2.0D);
    }

    private static Optional<LocalDate> date(Object value) {
        return TrackerJson.optionalString(value).map(LocalDate::parse);
    }

    private static Instant instant(Object value) {
        return TrackerJson.optionalString(value).map(Instant::parse).orElseGet(Instant::now);
    }

    private static double optionalDouble(Object value, double fallback) {
        return value instanceof Number number ? number.doubleValue() : fallback;
    }

    private static long optionalLong(Object value) {
        return value instanceof Number number ? number.longValue() : TrackerSearchResult.UNKNOWN_TOTAL;
    }

    private static String form(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private void requireOwned(TrackerEntry entry) {
        requireAuthenticated();
        if (!Objects.requireNonNull(entry, "entry must not be null").trackerId().equals(ID)) {
            throw new TrackerException("Tracking entry is not owned by Kitsu");
        }
    }

    private void requireAuthenticated() {
        if (!isAuthenticated()) {
            throw new TrackerException("Kitsu account is not authenticated");
        }
    }

    private static List<Double> scores() {
        List<Double> values = new ArrayList<>();
        for (int value = 0; value <= 20; value++) {
            values.add(value / 2.0D);
        }
        return List.copyOf(values);
    }
}
