package fr.vriege.anilib.tooling.archtests;

import fr.vriege.anilib.configuration.standard.StandardAnilib;
import fr.vriege.anilib.feature.library.LibraryItem;
import fr.vriege.anilib.feature.library.MediaKind;
import fr.vriege.anilib.feature.tracker.TrackerCapabilities;
import fr.vriege.anilib.feature.tracker.TrackerAuthorization;
import fr.vriege.anilib.feature.tracker.TrackerCredentials;
import fr.vriege.anilib.feature.tracker.TrackerEntry;
import fr.vriege.anilib.feature.tracker.TrackerSearchResult;
import fr.vriege.anilib.feature.tracker.TrackerStatus;
import fr.vriege.anilib.feature.tracker.anilist.AniListTracker;
import fr.vriege.anilib.feature.tracker.kitsu.KitsuTracker;
import fr.vriege.anilib.framework.http.AnilibHttpClient;
import fr.vriege.anilib.framework.http.HttpMethod;
import fr.vriege.anilib.framework.http.HttpRequest;
import fr.vriege.anilib.framework.http.HttpResponse;
import fr.vriege.anilib.kernel.StartedAnilib;

import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Comparator;
import java.util.stream.Stream;

final class FirstPartyTrackerTest {
    private FirstPartyTrackerTest() {
    }

    static int run() {
        Counter counter = new Counter();
        optInBundles(counter);
        aniListFlow(counter);
        kitsuFlow(counter);
        return counter.value;
    }

    private static void optInBundles(Counter counter) {
        Path directory = temporaryDirectory();
        try (StartedAnilib application = StandardAnilib.start(
                directory,
                (request, headers) -> {
                    throw new AssertionError("Provider bundles must not access the network during startup");
                },
                List.of())) {
            List<String> ids = application.capability(TrackerCapabilities.SERVICE).accounts().stream()
                    .map(account -> account.descriptor().id().value())
                    .sorted()
                    .toList();
            counter.check(ids.equals(List.of("anilist", "kitsu")),
                    "the standard product must select both first-party tracker bundles explicitly");
        } finally {
            deleteDirectory(directory);
        }
    }

    private static void aniListFlow(Counter counter) {
        ScriptedClient client = new ScriptedClient(
                json("{\"data\":{\"Viewer\":{\"id\":1,\"name\":\"alice\"}}}"),
                json("{\"data\":{\"Page\":{\"media\":[" + aniListMedia() + "]}}}"),
                json("{\"data\":{\"SaveMediaListEntry\":" + aniListEntry("PLANNING", 0, 0) + "}}"),
                json("{\"data\":{\"SaveMediaListEntry\":" + aniListEntry("CURRENT", 3, 8) + "}}"),
                json("{\"data\":{\"MediaList\":" + aniListEntry("CURRENT", 3, 8) + "}}"),
                json("{\"data\":{\"DeleteMediaListEntry\":{\"deleted\":true}}}"));
        AniListTracker tracker = new AniListTracker(client, "1234");
        TrackerAuthorization authorization = tracker.beginAuthorization().orElseThrow();
        String state = queryParameter(authorization.authorizationUri().getRawQuery(), "state");
        counter.check(authorization.authorizationUri().getHost().equals("anilist.co")
                        && queryParameter(authorization.authorizationUri().getRawQuery(), "client_id").equals("1234")
                        && queryParameter(
                                authorization.authorizationUri().getRawQuery(),
                                "response_type").equals("token"),
                "AniList login must begin on its official OAuth website");
        tracker.completeAuthorization(URI.create(
                authorization.callbackUri() + "#access_token=token-value&state=" + state));
        counter.check(tracker.isAuthenticated() && tracker.accountName().equals("alice"),
                "AniList OAuth callback must resolve the signed-in Viewer automatically");
        LibraryItem item = LibraryItem.create("Fixture anime", MediaKind.ANIME);
        TrackerSearchResult result = tracker.search("Fixture", MediaKind.ANIME).getFirst();
        TrackerEntry bound = tracker.bind(item, result);
        counter.check(result.remoteId().equals("42") && bound.status() == TrackerStatus.PLANNING,
                "AniList search results must bind through SaveMediaListEntry");
        TrackerEntry edited = bound.withStatus(TrackerStatus.WATCHING)
                .withProgress(3.0D)
                .withScore(OptionalDouble.of(8.0D))
                .withDates(Optional.of(LocalDate.of(2026, 8, 18)), Optional.empty());
        TrackerEntry updated = tracker.update(edited);
        TrackerEntry refreshed = tracker.refresh(updated);
        tracker.remove(refreshed);
        counter.check(refreshed.progress() == 3.0D && client.methods().stream().allMatch(HttpMethod.POST::equals),
                "AniList edits, refreshes, and removals must use authenticated GraphQL requests");
        counter.check(client.requests().stream().allMatch(request -> request.headers()
                        .getOrDefault("authorization", List.of()).equals(List.of("Bearer token-value"))),
                "AniList requests must carry the personal token only in the authorization header");
        tracker.logout();
        counter.check(!tracker.isAuthenticated() && client.isExhausted(),
                "AniList logout must clear the in-memory session after the complete flow");
    }

    private static void kitsuFlow(Counter counter) {
        ScriptedClient client = new ScriptedClient(
                json("{\"access_token\":\"kitsu-token\"}"),
                json("{\"data\":[{\"id\":\"7\",\"attributes\":{\"name\":\"bob\"}}]}"),
                json("{\"data\":[" + kitsuMedia() + "]}"),
                json("{\"data\":" + kitsuEntry("planned", false, 0, 0) + "}"),
                json("{\"data\":" + kitsuEntry("current", true, 4, 15) + "}"),
                json("{\"data\":" + kitsuEntry("current", true, 4, 15) + "}"),
                new HttpResponse(204, Map.of(), new byte[0], false));
        KitsuTracker tracker = new KitsuTracker(client);
        tracker.authenticate(TrackerCredentials.password("bob@example.test", "secret"));
        counter.check(tracker.isAuthenticated() && tracker.accountName().equals("bob"),
                "Kitsu password authentication must resolve the signed-in user");
        LibraryItem item = LibraryItem.create("Fixture manga", MediaKind.MANGA);
        TrackerSearchResult result = tracker.search("Fixture", MediaKind.MANGA).getFirst();
        TrackerEntry bound = tracker.bind(item, result);
        TrackerEntry edited = bound.withStatus(TrackerStatus.REREADING)
                .withProgress(4.0D)
                .withScore(OptionalDouble.of(7.5D));
        TrackerEntry updated = tracker.update(edited);
        TrackerEntry refreshed = tracker.refresh(updated);
        tracker.remove(refreshed);
        counter.check(refreshed.status() == TrackerStatus.REREADING && refreshed.score().orElseThrow() == 7.5D,
                "Kitsu refresh must preserve manga re-reading status and twenty-point ratings");
        counter.check(client.methods().containsAll(List.of(HttpMethod.POST, HttpMethod.PATCH, HttpMethod.DELETE)),
                "Kitsu must use JSON:API POST, PATCH, and DELETE operations");
        counter.check(client.requests().subList(1, client.requests().size()).stream().allMatch(request -> request
                        .headers().getOrDefault("authorization", List.of()).equals(List.of("Bearer kitsu-token"))),
                "Kitsu API requests must carry the short-lived access token");
        tracker.logout();
        counter.check(!tracker.isAuthenticated() && client.isExhausted(),
                "Kitsu logout must clear the in-memory session after the complete flow");
    }

    private static String aniListMedia() {
        return "{\"id\":42,\"type\":\"ANIME\",\"episodes\":12,\"chapters\":null,"
                + "\"siteUrl\":\"https://anilist.co/anime/42\","
                + "\"title\":{\"userPreferred\":\"Fixture anime\"}}";
    }

    private static String aniListEntry(String status, int progress, int score) {
        return "{\"id\":99,\"status\":\"" + status + "\",\"progress\":" + progress
                + ",\"repeat\":0,\"score\":" + score + ",\"private\":false,\"updatedAt\":1787000000,"
                + "\"startedAt\":{\"year\":2026,\"month\":8,\"day\":18},"
                + "\"completedAt\":{\"year\":null,\"month\":null,\"day\":null},"
                + "\"media\":" + aniListMedia() + "}";
    }

    private static String kitsuMedia() {
        return "{\"type\":\"manga\",\"id\":\"24\",\"attributes\":{"
                + "\"canonicalTitle\":\"Fixture manga\",\"chapterCount\":10}}";
    }

    private static String kitsuEntry(String status, boolean reconsuming, int progress, int rating) {
        return "{\"type\":\"libraryEntries\",\"id\":\"81\",\"attributes\":{"
                + "\"status\":\"" + status + "\",\"reconsuming\":" + reconsuming
                + ",\"progress\":" + progress + ",\"ratingTwenty\":" + rating
                + ",\"startedAt\":null,\"finishedAt\":null,\"private\":false,"
                + "\"updatedAt\":\"2026-08-18T12:00:00Z\"}}";
    }

    private static HttpResponse json(String value) {
        return new HttpResponse(
                200,
                Map.of("content-type", List.of("application/json")),
                value.getBytes(StandardCharsets.UTF_8),
                false);
    }

    private static String queryParameter(String query, String name) {
        for (String parameter : query.split("&")) {
            int separator = parameter.indexOf('=');
            String key = URLDecoder.decode(parameter.substring(0, separator), StandardCharsets.UTF_8);
            if (key.equals(name)) {
                return URLDecoder.decode(parameter.substring(separator + 1), StandardCharsets.UTF_8);
            }
        }
        throw new AssertionError("Missing OAuth parameter: " + name);
    }

    private static Path temporaryDirectory() {
        try {
            return Files.createTempDirectory("anilib-first-party-tracker-test");
        } catch (IOException exception) {
            throw new AssertionError("Unable to create tracker test directory", exception);
        }
    }

    private static void deleteDirectory(Path directory) {
        try (Stream<Path> entries = Files.walk(directory)) {
            for (Path entry : entries.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(entry);
            }
        } catch (IOException exception) {
            throw new AssertionError("Unable to clean tracker test directory", exception);
        }
    }

    private static final class ScriptedClient implements AnilibHttpClient {
        private final Deque<HttpResponse> responses;
        private final List<HttpRequest> requests = new ArrayList<>();

        private ScriptedClient(HttpResponse... values) {
            responses = new ArrayDeque<>(List.of(values));
        }

        @Override
        public HttpResponse execute(HttpRequest request) {
            requests.add(request);
            if (responses.isEmpty()) {
                throw new AssertionError("Unexpected tracker request: " + request.uri());
            }
            return responses.removeFirst();
        }

        private List<HttpRequest> requests() {
            return List.copyOf(requests);
        }

        private List<HttpMethod> methods() {
            return requests.stream().map(HttpRequest::method).toList();
        }

        private boolean isExhausted() {
            return responses.isEmpty();
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
