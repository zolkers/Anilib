package fr.vriege.anilib.tooling.archtests;

import fr.vriege.anilib.tooling.javaquality.CatalogueDetailsRouteRule;
import fr.vriege.anilib.tooling.javaquality.KotlinSource;
import fr.vriege.anilib.tooling.javaquality.ModuleMetadata;
import fr.vriege.anilib.tooling.javaquality.RepositorySnapshot;

import java.nio.file.Path;
import java.util.List;

final class CatalogueDetailsRouteRuleTest {
    private CatalogueDetailsRouteRuleTest() {
    }

    static int run() {
        Path root = Path.of("test-repository");
        ModuleMetadata module = new ModuleMetadata(
                "platform.compose",
                ModuleMetadata.Layer.PLATFORM,
                "UI_ADAPTER",
                "platform.compose",
                ModuleMetadata.Language.KOTLIN,
                List.of("feature.discovery.ui", "feature.library.ui"),
                root,
                root.resolve("module.properties"));
        CatalogueDetailsRouteRule rule = new CatalogueDetailsRouteRule();

        check(rule.analyze(snapshot(root, module, source(root, module, List.of(
                "fun destination() {",
                "    DetailsDestination()",
                "}")))).isEmpty(),
                "one canonical details destination must pass");
        check(rule.analyze(snapshot(root, module, source(root, module, List.of(
                "fun destination() {",
                "    DetailsDestination()",
                "    DetailsDestination()",
                "}")))).size() == 1,
                "a second details destination must be rejected");
        check(rule.analyze(snapshot(root, module, source(root, module, List.of(
                "fun destination() {",
                "    DetailsDestination()",
                "    browseDetailsTitle = selectedTitle",
                "}")))).size() == 1,
                "a Discovery-owned details overlay must be rejected");
        return 3;
    }

    private static KotlinSource source(
            Path root,
            ModuleMetadata module,
            List<String> lines) {
        Path path = root.resolve("AnilibApp.kt");
        return new KotlinSource(path, path.toAbsolutePath(), module, lines);
    }

    private static RepositorySnapshot snapshot(
            Path root,
            ModuleMetadata module,
            KotlinSource source) {
        return new RepositorySnapshot(root, List.of(module), List.of(), List.of(source), List.of());
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
