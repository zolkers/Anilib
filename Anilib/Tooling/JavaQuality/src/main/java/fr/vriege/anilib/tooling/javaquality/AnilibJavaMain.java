package fr.vriege.anilib.tooling.javaquality;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class AnilibJavaMain {
    private AnilibJavaMain() {
    }

    public static void main(String[] arguments) {
        Path root = List.of(arguments).stream()
                .filter(argument -> !argument.startsWith("--"))
                .findFirst()
                .map(Path::of)
                .orElseGet(() -> Path.of(""));
        RepositorySnapshot repository = new RepositoryScanner().scan(root);
        if (List.of(arguments).contains("--fix-direct-type-qualifiers")) {
            int changedSources = new DirectTypeQualifierFormatter().format(repository);
            System.out.println("AnilibJava: imported direct type qualifiers in " + changedSources + " source(s).");
            repository = new RepositoryScanner().scan(root);
        }
        List<Diagnostic> diagnostics = new ArrayList<>();
        for (AnilibJavaRule rule : AnilibJavaRuleRegistry.standard()) {
            diagnostics.addAll(rule.analyze(repository));
        }
        Collections.sort(diagnostics);

        if (diagnostics.isEmpty()) {
            System.out.println("AnilibJava: " + repository.javaSources().size()
                    + " Java and " + repository.kotlinSources().size()
                    + " Kotlin sources in " + repository.modules().size() + " modules passed.");
            return;
        }

        for (Diagnostic diagnostic : diagnostics) {
            System.err.println(diagnostic.path() + ":" + diagnostic.line()
                    + " [" + diagnostic.rule() + "] " + diagnostic.message());
        }
        System.err.println("AnilibJava: " + diagnostics.size() + " diagnostic(s).");
        System.exit(1);
    }
}
