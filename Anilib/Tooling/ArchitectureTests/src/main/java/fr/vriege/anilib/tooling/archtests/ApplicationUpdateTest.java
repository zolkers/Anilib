package fr.vriege.anilib.tooling.archtests;

import fr.vriege.anilib.feature.applicationupdate.ApplicationPlatform;
import fr.vriege.anilib.feature.applicationupdate.ApplicationUpdateSnapshot;
import fr.vriege.anilib.feature.applicationupdate.ApplicationVersion;
import fr.vriege.anilib.feature.applicationupdate.runtime.GitHubApplicationUpdateService;
import fr.vriege.anilib.framework.http.HttpResponse;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;

final class ApplicationUpdateTest {
    private ApplicationUpdateTest() {
    }

    static int run() {
        Counter counter = new Counter();
        counter.check(ApplicationVersion.parse("v1.10.0").compareTo(ApplicationVersion.parse("1.9.9")) > 0,
                "semantic version comparison must compare each numeric component");

        GitHubApplicationUpdateService update = service("""
                {"tag_name":"v1.2.0","html_url":"https://github.com/zolkers/Anilib/releases/tag/v1.2.0"}
                """);
        ApplicationUpdateSnapshot available = update.checkNow();
        counter.check(available.error().isEmpty(), "a valid release response must not report an error");
        counter.check(available.availableRelease().orElseThrow().version().display().equals("v1.2.0"),
                "a newer stable release must be exposed");
        counter.check(available.lastCheckedAt().isPresent(), "a successful check must record its time");

        ApplicationUpdateSnapshot current = service("""
                {"tag_name":"v1.0.0","html_url":"https://github.com/zolkers/Anilib/releases/tag/v1.0.0"}
                """).checkNow();
        counter.check(current.availableRelease().isEmpty(), "the installed version must not update to itself");

        ApplicationUpdateSnapshot malformed = service("{}").checkNow();
        counter.check(malformed.availableRelease().isEmpty(), "a malformed response must not expose a release");
        counter.check(malformed.error().isPresent(), "a malformed response must retain a user-visible error");
        return counter.value;
    }

    private static GitHubApplicationUpdateService service(String json) {
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        return new GitHubApplicationUpdateService(
                request -> new HttpResponse(200, Map.of(), body, false),
                ApplicationVersion.parse("1.0.0"),
                ApplicationPlatform.WINDOWS,
                URI.create("https://api.github.com/repos/zolkers/Anilib/releases/latest"));
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
