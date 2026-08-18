package fr.vriege.anilib.feature.settings;

public record BrowserPolicy(
        boolean javaScriptEnabled,
        boolean domStorageEnabled,
        boolean fileChooserEnabled,
        boolean popupsEnabled,
        boolean downloadsEnabled,
        boolean automaticChallengeRetry,
        int textZoomPercent) {
    public BrowserPolicy {
        if (textZoomPercent < 50 || textZoomPercent > 200) {
            throw new IllegalArgumentException("textZoomPercent must be between 50 and 200");
        }
    }

    public static BrowserPolicy defaults() {
        return new BrowserPolicy(true, true, true, true, true, true, 100);
    }
}
