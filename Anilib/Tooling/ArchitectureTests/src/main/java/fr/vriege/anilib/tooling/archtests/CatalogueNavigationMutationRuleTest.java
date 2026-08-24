package fr.vriege.anilib.tooling.archtests;

import fr.vriege.anilib.tooling.javaquality.CatalogueNavigationMutationRule;
import fr.vriege.anilib.tooling.javaquality.KotlinSource;
import fr.vriege.anilib.tooling.javaquality.ModuleMetadata;
import fr.vriege.anilib.tooling.javaquality.RepositorySnapshot;

import java.nio.file.Path;
import java.util.List;

final class CatalogueNavigationMutationRuleTest {
    private CatalogueNavigationMutationRuleTest() {
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
        CatalogueNavigationMutationRule rule = new CatalogueNavigationMutationRule();
        KotlinSource unsafe = source(root, module, List.of(
                "package fr.vriege.anilib.platform.compose",
                "fun catalogue() {",
                "    CatalogueContent(",
                "        open = { item ->",
                "            presentation.addToLibrary(item)",
                "        },",
                "        add = { item -> presentation.addToLibrary(item) },",
                "    )",
                "}"));
        check(rule.analyze(snapshot(root, module, unsafe)).size() == 1,
                "catalogue navigation must reject implicit Library writes while retaining explicit add actions");
        KotlinSource unsafeRemoval = source(root, module, List.of(
                "package fr.vriege.anilib.platform.compose",
                "fun catalogue() {",
                "    CatalogueContent(",
                "        open = { item -> presentation.removeFromLibrary(item.id()) },",
                "        add = { item -> presentation.addToLibrary(item) },",
                "    )",
                "}"));
        check(rule.analyze(snapshot(root, module, unsafeRemoval)).size() == 1,
                "catalogue navigation must reject implicit Library removals");

        KotlinSource safe = source(root, module, List.of(
                "package fr.vriege.anilib.platform.compose",
                "fun catalogue() {",
                "    CatalogueContent(",
                "        open = { item -> selectedItem = item },",
                "        add = { item -> presentation.addToLibrary(item) },",
                "    )",
                "}"));
        check(rule.analyze(snapshot(root, module, safe)).isEmpty(),
                "transient catalogue details must pass the navigation mutation rule");
        return 3;
    }

    private static KotlinSource source(
            Path root,
            ModuleMetadata module,
            List<String> lines) {
        Path path = root.resolve("DiscoveryScreen.kt");
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
