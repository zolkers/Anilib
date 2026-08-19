package fr.vriege.anilib.tooling.javaquality;

import java.util.ArrayList;
import java.util.List;

public final class EmptyModuleRule implements AnilibJavaRule {
    public EmptyModuleRule() {
    }

    @Override
    public String name() {
        return "empty-module";
    }

    @Override
    public List<Diagnostic> analyze(RepositorySnapshot repository) {
        List<Diagnostic> diagnostics = new ArrayList<>();
        for (ModuleMetadata module : repository.modules()) {
            boolean javaCode = repository.javaSources().stream()
                    .filter(source -> source.module().id().equals(module.id()))
                    .anyMatch(source -> !source.path().getFileName().toString().equals("module-info.java"));
            boolean kotlinCode = repository.kotlinSources().stream()
                    .anyMatch(source -> source.module().id().equals(module.id()));
            if (!javaCode && !kotlinCode) {
                diagnostics.add(new Diagnostic(
                        name(),
                        module.manifestPath(),
                        1,
                        "Module declares no Java or Kotlin production code"));
            }
        }
        return List.copyOf(diagnostics);
    }
}
