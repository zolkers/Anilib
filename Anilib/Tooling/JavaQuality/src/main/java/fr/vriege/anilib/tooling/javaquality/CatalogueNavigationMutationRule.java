package fr.vriege.anilib.tooling.javaquality;

import java.util.ArrayList;
import java.util.List;

public final class CatalogueNavigationMutationRule implements AnilibJavaRule {
    private static final String DISCOVERY_SCREEN = "DiscoveryScreen.kt";

    public CatalogueNavigationMutationRule() {
    }

    @Override
    public String name() {
        return "catalogue-navigation-mutation";
    }

    @Override
    public List<Diagnostic> analyze(RepositorySnapshot repository) {
        List<Diagnostic> diagnostics = new ArrayList<>();
        repository.kotlinSources().stream()
                .filter(source -> source.path().getFileName().toString().equals(DISCOVERY_SCREEN))
                .forEach(source -> validate(source, diagnostics));
        return diagnostics;
    }

    private static void validate(KotlinSource source, List<Diagnostic> diagnostics) {
        boolean catalogueOpen = false;
        int braceDepth = 0;
        for (int index = 0; index < source.lines().size(); index++) {
            String line = source.lines().get(index);
            if (!catalogueOpen && line.stripLeading().startsWith("open = {")) {
                catalogueOpen = true;
            }
            if (!catalogueOpen) {
                continue;
            }
            if (mutatesLibrary(line)) {
                diagnostics.add(new Diagnostic(
                        nameValue(),
                        source.path(),
                        index + 1,
                        "Opening a source catalogue title must not mutate Library; "
                                + "open transient source details and reserve writes for an explicit add action"));
            }
            braceDepth += braces(line);
            if (braceDepth <= 0) {
                catalogueOpen = false;
                braceDepth = 0;
            }
        }
    }

    private static boolean mutatesLibrary(String line) {
        return line.contains("addToLibrary(")
                || line.contains("removeFromLibrary(")
                || line.contains("deleteTitles(")
                || line.contains("setFavorite(");
    }

    private static int braces(String line) {
        int depth = 0;
        for (int index = 0; index < line.length(); index++) {
            depth += switch (line.charAt(index)) {
                case '{' -> 1;
                case '}' -> -1;
                default -> 0;
            };
        }
        return depth;
    }

    private static String nameValue() {
        return "catalogue-navigation-mutation";
    }
}
