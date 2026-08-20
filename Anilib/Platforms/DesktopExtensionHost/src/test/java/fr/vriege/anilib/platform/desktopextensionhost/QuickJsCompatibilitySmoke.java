package fr.vriege.anilib.platform.desktopextensionhost;

import fr.vriege.anilib.platform.desktopextensionhost.compat.quickjs.QuickJs;
import java.io.Closeable;

final class QuickJsCompatibilitySmoke {
    private static final String EPISODE_PROJECTION =
            "JSON.stringify(Object.keys(this).filter(k => /^eps[0-9]+$/.test(k))"
                    + ".sort((a, b) => parseInt(a.slice(3)) - parseInt(b.slice(3)))"
                    + ".map(k => this[k]))";

    private QuickJsCompatibilitySmoke() {
    }

    static void verify() {
        try (QuickJs quickJs = QuickJs.create()) {
            Closeable closeable = quickJs;
            if (closeable != quickJs) {
                throw new IllegalStateException("QuickJs must retain its java.io.Closeable ABI");
            }
            quickJs.evaluate("""
                    // Representative Anime-Sama episodes.js data.
                    var eps10 = ['https://video.example/10'];
                    const ignored = "eps2 = ['wrong']";
                    let eps2 = ["https://video.example/2?a=1&b=2", 'escaped\\u002fpath'];
                    var eps1 = [
                      'https://video.example/1',
                    ];
                    """);
            Object result = quickJs.evaluate(EPISODE_PROJECTION);
            String expected = "[[\"https://video.example/1\"],"
                    + "[\"https://video.example/2?a=1&b=2\",\"escaped/path\"],"
                    + "[\"https://video.example/10\"]]";
            if (!expected.equals(result)) {
                throw new IllegalStateException("QuickJS episode projection failed: " + result);
            }
        }
    }
}
