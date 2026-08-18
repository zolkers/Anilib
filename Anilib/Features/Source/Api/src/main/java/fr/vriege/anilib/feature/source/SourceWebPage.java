package fr.vriege.anilib.feature.source;

import fr.vriege.anilib.foundation.validation.Preconditions;

import java.net.URI;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

public record SourceWebPage(
        URI location,
        Map<String, String> headers,
        Optional<String> userAgent,
        Set<String> completionCookies) {
    private static final Pattern TOKEN = Pattern.compile("[!#$%&'*+.^_`|~0-9A-Za-z-]+");

    public SourceWebPage {
        location = webLocation(location);
        headers = SourceSubtitleTrack.immutableHeaders(headers);
        headers.forEach(SourceWebPage::validateHeader);
        userAgent = Preconditions.requireNonNull(userAgent, "userAgent")
                .map(String::strip)
                .map(value -> Preconditions.requireNonBlank(value, "userAgent"))
                .map(SourceWebPage::safeUserAgent);
        completionCookies = immutableCookieNames(completionCookies);
    }

    public static SourceWebPage of(URI location) {
        return new SourceWebPage(location, Map.of(), Optional.empty(), Set.of());
    }

    private static URI webLocation(URI value) {
        URI location = Preconditions.requireNonNull(value, "location").normalize();
        String scheme = location.getScheme();
        if (!location.isAbsolute() || location.getHost() == null || location.getUserInfo() != null
                || !("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))) {
            throw new IllegalArgumentException(
                    "location must be an absolute HTTP(S) URI without user info");
        }
        return location;
    }

    private static void validateHeader(String name, String value) {
        if (!TOKEN.matcher(name).matches() || value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0) {
            throw new IllegalArgumentException("browser headers must use valid HTTP field syntax");
        }
        if (name.equalsIgnoreCase("Cookie")
                || name.equalsIgnoreCase("Set-Cookie")
                || name.equalsIgnoreCase("User-Agent")
                || name.equalsIgnoreCase("Authorization")
                || name.equalsIgnoreCase("Proxy-Authorization")) {
            throw new IllegalArgumentException(
                    "credential, cookie, and User-Agent headers are not accepted as browser headers");
        }
    }

    private static String safeUserAgent(String value) {
        if (value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0) {
            throw new IllegalArgumentException("userAgent must not contain line breaks");
        }
        return value;
    }

    private static Set<String> immutableCookieNames(Set<String> values) {
        Preconditions.requireNonNull(values, "completionCookies");
        Set<String> result = new LinkedHashSet<>();
        for (String value : values) {
            String name = Preconditions.requireNonBlank(value, "completion cookie").strip();
            if (!TOKEN.matcher(name).matches()) {
                throw new IllegalArgumentException("completion cookie must be a valid cookie name");
            }
            if (!result.add(name)) {
                throw new IllegalArgumentException("completion cookie names must be unique");
            }
        }
        return Set.copyOf(result);
    }
}
