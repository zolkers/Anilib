package fr.vriege.anilib.tooling.archtests;

import fr.vriege.anilib.tooling.javaquality.DirectTypeQualifierFormatter;
import fr.vriege.anilib.tooling.javaquality.DirectTypeQualifierRule;
import fr.vriege.anilib.tooling.javaquality.JavaSource;
import fr.vriege.anilib.tooling.javaquality.KotlinSource;
import fr.vriege.anilib.tooling.javaquality.ModuleMetadata;
import fr.vriege.anilib.tooling.javaquality.RepositorySnapshot;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

final class DirectTypeQualifierRuleTest {
    private DirectTypeQualifierRuleTest() {
    }

    static int run() {
        Path root = Path.of("test-repository");
        ModuleMetadata module = module();
        JavaSource javaSource = new JavaSource(
                root.resolve("Example.java"),
                root.resolve("Example.java").toAbsolutePath(),
                module,
                List.of(
                        "package fr.vriege.anilib.example;",
                        "import java.util.List;",
                        "final class Example { java.util.ArrayList<String> values; }",
                        "java.sql.Date databaseDate;",
                        "java.util.Date utilityDate;",
                        "// java.util.HashMap is ignored",
                        "String name = \"java.util.TreeMap\";"));
        KotlinSource kotlinSource = new KotlinSource(
                root.resolve("Example.kt"),
                root.resolve("Example.kt").toAbsolutePath(),
                module,
                List.of(
                        "package fr.vriege.anilib.example",
                        "import java.util.List",
                        "fun example(value: java.util.ArrayList<String>) = value",
                        "val name = \"java.util.TreeMap\""));
        RepositorySnapshot snapshot = new RepositorySnapshot(
                root,
                List.of(module),
                List.of(javaSource),
                List.of(kotlinSource),
                List.of());
        DirectTypeQualifierRule rule = new DirectTypeQualifierRule();
        var diagnostics = rule.analyze(snapshot);
        check(diagnostics.size() == 2, "direct Java and Kotlin type qualifiers must be rejected");
        check(diagnostics.stream().noneMatch(diagnostic -> diagnostic.message().contains("Date")),
                "qualifiers must remain available for unavoidable simple-name collisions");
        check(diagnostics.stream().allMatch(diagnostic -> diagnostic.message().startsWith("Import ")),
                "diagnostics must explain the import-based correction");
        formatterPreservesTextBlockImports(root, module);
        return 7;
    }

    private static void formatterPreservesTextBlockImports(Path root, ModuleMetadata module) {
        try {
            Path sourceFile = Files.createTempFile("anilib-direct-qualifier-", ".java");
            List<String> lines = List.of(
                    "package fr.vriege.anilib.example;",
                    "import java.util.List;",
                    "final class Fixture {",
                    "    String source = \"\"\"",
                    "            import example.fixture.Source;",
                    "            \"\"\";",
                    "    java.util.ArrayList<String> values;",
                    "}");
            Files.write(sourceFile, lines, StandardCharsets.UTF_8);
            JavaSource source = new JavaSource(root.resolve("Fixture.java"), sourceFile, module, lines);
            RepositorySnapshot snapshot = new RepositorySnapshot(
                    root,
                    List.of(module),
                    List.of(source),
                    List.of(),
                    List.of());
            int changed = new DirectTypeQualifierFormatter().format(snapshot);
            List<String> formatted = Files.readAllLines(sourceFile, StandardCharsets.UTF_8);
            check(changed == 1, "formatter must report the rewritten source");
            check(formatted.contains("import java.util.ArrayList;"),
                    "formatter must add the real source import");
            check(formatted.contains("            import example.fixture.Source;"),
                    "formatter must leave imports inside text blocks untouched");
            check(formatted.indexOf("import java.util.ArrayList;") < formatted.indexOf("final class Fixture {"),
                    "formatter must insert imports before the type declaration");
            Files.deleteIfExists(sourceFile);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static ModuleMetadata module() {
        Path directory = Path.of("Anilib", "Platforms", "Example");
        return new ModuleMetadata(
                "platform.example",
                ModuleMetadata.Layer.PLATFORM,
                "APP",
                "platform.example",
                ModuleMetadata.Language.JAVA_KOTLIN,
                List.of(),
                directory,
                directory.resolve("module.properties"));
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
