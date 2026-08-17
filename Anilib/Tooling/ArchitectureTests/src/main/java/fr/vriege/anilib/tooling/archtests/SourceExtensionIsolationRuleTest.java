package fr.vriege.anilib.tooling.archtests;

import fr.vriege.anilib.tooling.javaquality.Diagnostic;
import fr.vriege.anilib.tooling.javaquality.JavaSource;
import fr.vriege.anilib.tooling.javaquality.ModuleMetadata;
import fr.vriege.anilib.tooling.javaquality.RepositorySnapshot;
import fr.vriege.anilib.tooling.javaquality.SourceExtensionIsolationRule;

import java.nio.file.Path;
import java.util.List;

/** Focused checks for the repository-enforced source extension sandbox boundary. */
final class SourceExtensionIsolationRuleTest {
    private SourceExtensionIsolationRuleTest() {
    }

    static int run() {
        Path root = Path.of("test-repository");
        ModuleMetadata safeModule = module(
                "extension.safe",
                "BUNDLE",
                List.of("foundation", "framework.http.api", "feature.source.api"));
        JavaSource safeSource = source(
                safeModule,
                "SafeSource.java",
                List.of(
                        "package fr.vriege.anilib.extension.safe;",
                        "import fr.vriege.anilib.feature.source.SourceExtensionContext;",
                        "import fr.vriege.anilib.framework.http.HttpRequest;"));
        SourceExtensionIsolationRule rule = new SourceExtensionIsolationRule();
        check(rule.analyze(snapshot(root, safeModule, safeSource)).isEmpty(),
                "an extension using only its SDK context must pass isolation checks");

        ModuleMetadata unsafeModule = module(
                "extension.unsafe",
                "RUNTIME",
                List.of("feature.source.api", "feature.network.api"));
        JavaSource unsafeSource = source(
                unsafeModule,
                "UnsafeSource.java",
                List.of(
                        "package fr.vriege.anilib.extension.unsafe;",
                        "import java.net.http.HttpClient;"));
        List<Diagnostic> diagnostics = rule.analyze(snapshot(root, unsafeModule, unsafeSource));
        check(diagnostics.size() == 3,
                "an extension bypass must report role, dependency, and direct network access");
        check(diagnostics.stream().anyMatch(diagnostic -> diagnostic.message().contains("granted")),
                "extension bypass diagnostics must direct authors to the granted context");
        return 3;
    }

    private static ModuleMetadata module(
            String id,
            String role,
            List<String> dependencies) {
        Path directory = Path.of("Anilib", "Extensions", id);
        return new ModuleMetadata(
                id,
                ModuleMetadata.Layer.EXTENSION,
                role,
                id,
                ModuleMetadata.Language.JAVA,
                dependencies,
                directory,
                directory.resolve("module.properties"));
    }

    private static JavaSource source(ModuleMetadata module, String name, List<String> lines) {
        Path path = module.directory().resolve("src/main/java").resolve(name);
        return new JavaSource(path, path.toAbsolutePath(), module, lines);
    }

    private static RepositorySnapshot snapshot(
            Path root,
            ModuleMetadata module,
            JavaSource source) {
        return new RepositorySnapshot(
                root,
                List.of(module),
                List.of(source),
                List.of(),
                List.of());
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
