package fr.vriege.anilib.tooling.javaquality;

import java.util.ArrayList;
import java.util.List;

public final class CatalogueDetailsRouteRule implements AnilibJavaRule {
    private static final String APP_SOURCE = "AnilibApp.kt";

    public CatalogueDetailsRouteRule() {
    }

    @Override
    public String name() {
        return "catalogue-details-route";
    }

    @Override
    public List<Diagnostic> analyze(RepositorySnapshot repository) {
        List<Diagnostic> diagnostics = new ArrayList<>();
        repository.kotlinSources().stream()
                .filter(source -> source.path().getFileName().toString().equals(APP_SOURCE))
                .forEach(source -> validate(source, diagnostics));
        return diagnostics;
    }

    private static void validate(KotlinSource source, List<Diagnostic> diagnostics) {
        int destinationCount = 0;
        for (int index = 0; index < source.lines().size(); index++) {
            String line = source.lines().get(index);
            if (line.contains("DetailsDestination(")) {
                destinationCount++;
                if (destinationCount > 1) {
                    diagnostics.add(new Diagnostic(
                            "catalogue-details-route",
                            source.path(),
                            index + 1,
                            "Media details must use the single canonical LibraryPage.DETAILS destination; "
                                    + "do not compose a second details route over Discovery"));
                }
            }
            if (line.contains("browseDetailsTitle") || line.contains("overlayNavigator")) {
                diagnostics.add(new Diagnostic(
                        "catalogue-details-route",
                        source.path(),
                        index + 1,
                        "Discovery must delegate to the canonical library navigator "
                                + "instead of owning a details overlay"));
            }
            if (line.contains("navigate(transition)")
                    && nextCodeLine(source, index).contains("openSection(")) {
                diagnostics.add(new Diagnostic(
                        "catalogue-details-route",
                        source.path(),
                        index + 1,
                        "Open the media section before applying its details transition; "
                                + "opening the section afterwards resets the canonical route"));
            }
        }
        if (destinationCount == 0) {
            diagnostics.add(new Diagnostic(
                    "catalogue-details-route",
                    source.path(),
                    1,
                    "AnilibApp must expose exactly one canonical DetailsDestination"));
        }
    }

    private static String nextCodeLine(KotlinSource source, int currentIndex) {
        for (int index = currentIndex + 1; index < source.lines().size(); index++) {
            String line = source.lines().get(index).strip();
            if (!line.isEmpty()) {
                return line;
            }
        }
        return "";
    }
}
