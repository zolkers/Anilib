package fr.vriege.anilib.feature.tracker.anilist;

import fr.vriege.anilib.feature.library.LibraryItem;
import fr.vriege.anilib.feature.library.MediaKind;
import fr.vriege.anilib.feature.tracker.Tracker;
import fr.vriege.anilib.feature.tracker.TrackerAuthentication;
import fr.vriege.anilib.feature.tracker.TrackerAuthorization;
import fr.vriege.anilib.feature.tracker.TrackerCredentials;
import fr.vriege.anilib.feature.tracker.TrackerDescriptor;
import fr.vriege.anilib.feature.tracker.TrackerEntry;
import fr.vriege.anilib.feature.tracker.TrackerException;
import fr.vriege.anilib.feature.tracker.TrackerId;
import fr.vriege.anilib.feature.tracker.TrackerIcon;
import fr.vriege.anilib.feature.tracker.TrackerSearchResult;
import fr.vriege.anilib.feature.tracker.TrackerSdk;
import fr.vriege.anilib.feature.tracker.TrackerStatus;
import fr.vriege.anilib.feature.tracker.providersupport.TrackerJson;
import fr.vriege.anilib.framework.http.AnilibHttpClient;
import fr.vriege.anilib.framework.http.HttpMethod;
import fr.vriege.anilib.framework.http.HttpRequest;

import java.net.URI;
import java.net.URLDecoder;
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
import java.util.UUID;
import fr.vriege.anilib.feature.library.LibraryItemId;

public final class AniListTracker implements Tracker {
    private static final URI ENDPOINT = URI.create("https://graphql.anilist.co/");
    private static final URI AUTHORIZE_ENDPOINT = URI.create("https://anilist.co/api/v2/oauth/authorize");
    public static final URI DEFAULT_CALLBACK = URI.create("http://127.0.0.1:43697/oauth/anilist/callback");
    private static final TrackerId ID = TrackerId.of("anilist");
    private static final TrackerDescriptor DESCRIPTOR = new TrackerDescriptor(
            ID,
            "AniList",
            new TrackerIcon("A", 0x02A9FF),
            TrackerSdk.API_VERSION,
            Set.of(MediaKind.ANIME, MediaKind.MANGA),
            TrackerAuthentication.OAUTH,
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
    private static final String ENTRY_FIELDS = "id status progress repeat score private updatedAt "
            + "startedAt { year month day } completedAt { year month day } "
            + "media { id type episodes chapters siteUrl title { userPreferred } }";
    private final AnilibHttpClient client;
    private final String clientId;
    private final URI callbackUri;
    private String token;
    private String accountName = "";
    private String authorizationState;

    public AniListTracker(AnilibHttpClient client) {
        this(client, "", DEFAULT_CALLBACK);
    }

    public AniListTracker(AnilibHttpClient client, String clientId) {
        this(client, clientId, DEFAULT_CALLBACK);
    }

    public AniListTracker(AnilibHttpClient client, String clientId, URI callbackUri) {
        this.client = Objects.requireNonNull(client, "client must not be null");
        this.clientId = Objects.requireNonNull(clientId, "clientId must not be null").strip();
        this.callbackUri = requireLoopbackCallback(callbackUri);
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
        if (value.authentication() != TrackerAuthentication.OAUTH) {
            throw new TrackerException("AniList requires an OAuth authorization result");
        }
        token = value.secret();
        try {
            Map<String, Object> viewer = TrackerJson.memberObject(data(
                    "query { Viewer { id name } }", Map.of()), "Viewer");
            accountName = TrackerJson.memberString(viewer, "name");
        } catch (RuntimeException exception) {
            logout();
            throw exception;
        }
    }

    @Override
    public Optional<TrackerAuthorization> beginAuthorization() {
        if (clientId.isBlank()) {
            return Optional.empty();
        }
        authorizationState = UUID.randomUUID().toString();
        String query = "client_id=" + encode(clientId)
                + "&response_type=token&state=" + encode(authorizationState);
        return Optional.of(new TrackerAuthorization(URI.create(AUTHORIZE_ENDPOINT + "?" + query), callbackUri));
    }

    @Override
    public void completeAuthorization(URI callbackUri) {
        TrackerAuthorization authorization = new TrackerAuthorization(AUTHORIZE_ENDPOINT, this.callbackUri);
        URI callback = Objects.requireNonNull(callbackUri, "callbackUri must not be null");
        if (!authorization.accepts(callback)) {
            throw new TrackerException("AniList returned an unexpected OAuth callback");
        }
        Map<String, String> values = new LinkedHashMap<>(parameters(callback.getRawQuery()));
        parameters(callback.getRawFragment()).forEach(values::putIfAbsent);
        String expectedState = authorizationState;
        authorizationState = null;
        if (expectedState == null || !expectedState.equals(values.get("state"))) {
            throw new TrackerException("AniList OAuth state did not match the active login");
        }
        String oauthError = values.get("error");
        if (oauthError != null) {
            throw new TrackerException("AniList authorization failed: " + oauthError);
        }
        String accessToken = values.get("access_token");
        if (accessToken == null || accessToken.isBlank()) {
            throw new TrackerException("AniList authorization did not return an access token");
        }
        authenticate(TrackerCredentials.oauthResult(accessToken));
    }

    @Override
    public void logout() {
        token = null;
        accountName = "";
        authorizationState = null;
    }

    @Override
    public List<TrackerSearchResult> search(String query, MediaKind kind) {
        requireAuthenticated();
        String value = Objects.requireNonNull(query, "query must not be null").strip();
        if (value.isEmpty()) {
            throw new TrackerException("AniList search query must not be blank");
        }
        String graph = "query ($search: String!, $type: MediaType!) { Page(page: 1, perPage: 25) { "
                + "media(search: $search, type: $type, sort: SEARCH_MATCH) { "
                + "id type episodes chapters siteUrl title { userPreferred } } } }";
        Map<String, Object> page = TrackerJson.memberObject(data(
                graph,
                Map.of("search", value, "type", mediaType(kind))), "Page");
        List<TrackerSearchResult> results = new ArrayList<>();
        for (Object child : TrackerJson.memberArray(page, "media")) {
            results.add(searchResult(TrackerJson.object(child, "AniList media")));
        }
        return List.copyOf(results);
    }

    @Override
    public TrackerEntry bind(LibraryItem item, TrackerSearchResult result) {
        requireAuthenticated();
        LibraryItem libraryItem = Objects.requireNonNull(item, "item must not be null");
        TrackerSearchResult match = Objects.requireNonNull(result, "result must not be null");
        if (!match.trackerId().equals(ID) || match.kind() != libraryItem.kind()) {
            throw new TrackerException("AniList search result does not match this library title");
        }
        String mutation = "mutation ($mediaId: Int!) { SaveMediaListEntry(mediaId: $mediaId, "
                + "status: PLANNING) { " + ENTRY_FIELDS + " } }";
        Map<String, Object> saved = TrackerJson.memberObject(data(
                mutation,
                Map.of("mediaId", integerId(match.remoteId()))), "SaveMediaListEntry");
        return entry(libraryItem, saved);
    }

    @Override
    public TrackerEntry update(TrackerEntry entry) {
        requireOwned(entry);
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("id", integerId(entry.remoteId()));
        variables.put("status", remoteStatus(entry.status()));
        variables.put("progress", (long) Math.floor(entry.progress()));
        variables.put("score", entry.score().isPresent() ? entry.score().getAsDouble() : null);
        variables.put("startedAt", fuzzyDate(entry.startDate()));
        variables.put("completedAt", fuzzyDate(entry.finishDate()));
        variables.put("private", entry.privateEntry());
        String mutation = "mutation ($id: Int!, $status: MediaListStatus!, $progress: Int!, "
                + "$score: Float, $startedAt: FuzzyDateInput, $completedAt: FuzzyDateInput, "
                + "$private: Boolean!) { SaveMediaListEntry(id: $id, status: $status, "
                + "progress: $progress, score: $score, startedAt: $startedAt, completedAt: $completedAt, "
                + "private: $private) { " + ENTRY_FIELDS + " } }";
        Map<String, Object> saved = TrackerJson.memberObject(data(
                mutation,
                variables), "SaveMediaListEntry");
        return entry(entry.libraryItemId(), saved);
    }

    @Override
    public TrackerEntry refresh(TrackerEntry entry) {
        requireOwned(entry);
        String query = "query ($id: Int!) { MediaList(id: $id) { " + ENTRY_FIELDS + " } }";
        return entry(entry.libraryItemId(), TrackerJson.memberObject(data(
                query,
                Map.of("id", integerId(entry.remoteId()))), "MediaList"));
    }

    @Override
    public void remove(TrackerEntry entry) {
        requireOwned(entry);
        String mutation = "mutation ($id: Int!) { DeleteMediaListEntry(id: $id) { deleted } }";
        Map<String, Object> deleted = TrackerJson.memberObject(data(
                mutation,
                Map.of("id", integerId(entry.remoteId()))), "DeleteMediaListEntry");
        if (!TrackerJson.booleanValue(deleted.get("deleted"), false)) {
            throw new TrackerException("AniList did not remove the tracking entry");
        }
    }

    private Map<String, Object> data(String query, Map<String, Object> variables) {
        requireAuthenticated();
        byte[] body = TrackerJson.encode(Map.of("query", query, "variables", variables))
                .getBytes(StandardCharsets.UTF_8);
        HttpRequest request = HttpRequest.builder(ENDPOINT)
                .method(HttpMethod.POST)
                .header("content-type", "application/json")
                .header("accept", "application/json")
                .header("authorization", "Bearer " + token)
                .minimumInterval(Duration.ofMillis(350))
                .body(body)
                .build();
        Map<String, Object> root = TrackerJson.object(TrackerJson.execute(client, request), "AniList response");
        Object errors = root.get("errors");
        if (errors instanceof List<?> values && !values.isEmpty()) {
            Map<String, Object> error = TrackerJson.object(values.getFirst(), "AniList error");
            throw new TrackerException(TrackerJson.optionalString(error.get("message"))
                    .orElse("AniList rejected the request"));
        }
        return TrackerJson.memberObject(root, "data");
    }

    private static TrackerSearchResult searchResult(Map<String, Object> media) {
        MediaKind kind = "ANIME".equals(TrackerJson.memberString(media, "type"))
                ? MediaKind.ANIME
                : MediaKind.MANGA;
        long total = optionalLong(media.get(kind == MediaKind.ANIME ? "episodes" : "chapters"));
        return new TrackerSearchResult(
                ID,
                Long.toString(TrackerJson.longValue(media.get("id"), "AniList media id")),
                TrackerJson.memberString(TrackerJson.memberObject(media, "title"), "userPreferred"),
                kind,
                total,
                uri(media.get("siteUrl")));
    }

    private static TrackerEntry entry(LibraryItem item, Map<String, Object> value) {
        return entry(item.id(), value);
    }

    private static TrackerEntry entry(
            LibraryItemId libraryItemId,
            Map<String, Object> value) {
        Map<String, Object> media = TrackerJson.memberObject(value, "media");
        MediaKind kind = "ANIME".equals(TrackerJson.memberString(media, "type"))
                ? MediaKind.ANIME
                : MediaKind.MANGA;
        long total = optionalLong(media.get(kind == MediaKind.ANIME ? "episodes" : "chapters"));
        return new TrackerEntry(
                libraryItemId,
                ID,
                Long.toString(TrackerJson.longValue(value.get("id"), "AniList list id")),
                TrackerJson.memberString(TrackerJson.memberObject(media, "title"), "userPreferred"),
                optionalDouble(value.get("progress"), 0.0D),
                total,
                status(TrackerJson.memberString(value, "status"), kind),
                optionalScore(value.get("score")),
                fuzzyDate(value.get("startedAt")),
                fuzzyDate(value.get("completedAt")),
                TrackerJson.booleanValue(value.get("private"), false),
                uri(media.get("siteUrl")),
                instant(value.get("updatedAt")));
    }

    private static String mediaType(MediaKind kind) {
        return switch (Objects.requireNonNull(kind, "kind must not be null")) {
            case ANIME -> "ANIME";
            case MANGA -> "MANGA";
            default -> throw new TrackerException("AniList supports only anime and manga");
        };
    }

    private static String remoteStatus(TrackerStatus status) {
        return switch (status) {
            case WATCHING, READING -> "CURRENT";
            case COMPLETED -> "COMPLETED";
            case ON_HOLD -> "PAUSED";
            case PLANNING -> "PLANNING";
            case DROPPED -> "DROPPED";
            case REWATCHING, REREADING -> "REPEATING";
        };
    }

    private static TrackerStatus status(String value, MediaKind kind) {
        return switch (value) {
            case "CURRENT" -> kind == MediaKind.ANIME ? TrackerStatus.WATCHING : TrackerStatus.READING;
            case "COMPLETED" -> TrackerStatus.COMPLETED;
            case "PAUSED" -> TrackerStatus.ON_HOLD;
            case "PLANNING" -> TrackerStatus.PLANNING;
            case "DROPPED" -> TrackerStatus.DROPPED;
            case "REPEATING" -> kind == MediaKind.ANIME
                    ? TrackerStatus.REWATCHING
                    : TrackerStatus.REREADING;
            default -> throw new TrackerException("Unsupported AniList status: " + value);
        };
    }

    private static Map<String, Object> fuzzyDate(Optional<LocalDate> date) {
        return date.<Map<String, Object>>map(value -> Map.of(
                        "year", value.getYear(),
                        "month", value.getMonthValue(),
                        "day", value.getDayOfMonth()))
                .orElse(null);
    }

    private static Optional<LocalDate> fuzzyDate(Object value) {
        if (!(value instanceof Map<?, ?>)) {
            return Optional.empty();
        }
        Map<String, Object> date = TrackerJson.object(value, "AniList date");
        int year = (int) optionalLong(date.get("year"));
        int month = (int) optionalLong(date.get("month"));
        int day = (int) optionalLong(date.get("day"));
        return year > 0 && month > 0 && day > 0
                ? Optional.of(LocalDate.of(year, month, day))
                : Optional.empty();
    }

    private static OptionalDouble optionalScore(Object value) {
        double score = optionalDouble(value, 0.0D);
        return score <= 0.0D ? OptionalDouble.empty() : OptionalDouble.of(score);
    }

    private static double optionalDouble(Object value, double fallback) {
        return value instanceof Number number ? number.doubleValue() : fallback;
    }

    private static long optionalLong(Object value) {
        return value instanceof Number number ? number.longValue() : TrackerSearchResult.UNKNOWN_TOTAL;
    }

    private static Optional<URI> uri(Object value) {
        return TrackerJson.optionalString(value).map(URI::create);
    }

    private static Map<String, String> parameters(String raw) {
        Map<String, String> values = new LinkedHashMap<>();
        if (raw == null || raw.isBlank()) {
            return values;
        }
        for (String pair : raw.split("&")) {
            int separator = pair.indexOf('=');
            String name = decode(separator < 0 ? pair : pair.substring(0, separator));
            String value = decode(separator < 0 ? "" : pair.substring(separator + 1));
            if (!name.isBlank()) {
                values.putIfAbsent(name, value);
            }
        }
        return values;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private static Instant instant(Object value) {
        return value instanceof Number number ? Instant.ofEpochSecond(number.longValue()) : Instant.now();
    }

    private static int integerId(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw new TrackerException("AniList identity is not numeric", exception);
        }
    }

    private static URI requireLoopbackCallback(URI value) {
        URI callback = Objects.requireNonNull(value, "callbackUri must not be null");
        if (!"http".equalsIgnoreCase(callback.getScheme())
                || !"127.0.0.1".equals(callback.getHost())
                || callback.getPort() < 1
                || callback.getPort() > 65535
                || callback.getRawPath() == null
                || !callback.getRawPath().matches("/[A-Za-z0-9/_-]+")
                || callback.getRawQuery() != null
                || callback.getRawFragment() != null
                || callback.getUserInfo() != null) {
            throw new IllegalArgumentException(
                    "AniList callbackUri must be an explicit http://127.0.0.1:<port>/path loopback URI");
        }
        return callback;
    }

    private void requireOwned(TrackerEntry entry) {
        requireAuthenticated();
        if (!Objects.requireNonNull(entry, "entry must not be null").trackerId().equals(ID)) {
            throw new TrackerException("Tracking entry is not owned by AniList");
        }
    }

    private void requireAuthenticated() {
        if (!isAuthenticated()) {
            throw new TrackerException("AniList account is not authenticated");
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
