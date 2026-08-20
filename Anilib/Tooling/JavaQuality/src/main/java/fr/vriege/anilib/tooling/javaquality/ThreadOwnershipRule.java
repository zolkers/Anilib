package fr.vriege.anilib.tooling.javaquality;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public final class ThreadOwnershipRule implements AnilibJavaRule {
    private static final String CONCURRENCY_MODULE = "framework.concurrency.runtime";
    private static final Pattern DIRECT_EXECUTORS = Pattern.compile(
            "(^|[^a-zA-Z0-9_.])Executors\\.");

    public ThreadOwnershipRule() {
    }

    @Override
    public String name() {
        return "thread-ownership";
    }

    @Override
    public List<Diagnostic> analyze(RepositorySnapshot repository) {
        List<Diagnostic> diagnostics = new ArrayList<>();
        repository.javaSources().stream()
                .filter(source -> isProduction(source.path().toString()))
                .filter(source -> isChecked(source.module()))
                .forEach(source -> inspectJava(source, diagnostics));
        repository.kotlinSources().stream()
                .filter(source -> isProduction(source.path().toString()))
                .filter(source -> isChecked(source.module()))
                .forEach(source -> inspectKotlin(source, diagnostics));
        return diagnostics;
    }

    private void inspectJava(JavaSource source, List<Diagnostic> diagnostics) {
        for (int index = 0; index < source.lines().size(); index++) {
            String line = source.lines().get(index);
            if (DIRECT_EXECUTORS.matcher(line).find()) {
                diagnostics.add(diagnostic(source.path(), index,
                        "Executors must be created through ManagedExecutors"));
            }
            if (line.contains("new Thread(")) {
                diagnostics.add(diagnostic(source.path(), index,
                        "Threads must be created through ManagedExecutors"));
            }
        }
    }

    private void inspectKotlin(KotlinSource source, List<Diagnostic> diagnostics) {
        for (int index = 0; index < source.lines().size(); index++) {
            String line = source.lines().get(index);
            if (DIRECT_EXECUTORS.matcher(line).find()) {
                diagnostics.add(diagnostic(source.path(), index,
                        "Executors must be created through ManagedExecutors"));
            }
            if (line.matches(".*\\bThread\\s*\\(.*")) {
                diagnostics.add(diagnostic(source.path(), index,
                        "Threads must be created through ManagedExecutors"));
            }
        }
    }

    private static boolean isProduction(String path) {
        String normalized = path.replace('\\', '/');
        return normalized.contains("/src/main/") || normalized.contains("/src/shared/");
    }

    private static boolean isChecked(ModuleMetadata module) {
        return module.layer() != ModuleMetadata.Layer.TOOLING
                && !module.id().equals(CONCURRENCY_MODULE);
    }

    private Diagnostic diagnostic(Path path, int zeroBasedLine, String message) {
        return new Diagnostic(name(), path, zeroBasedLine + 1, message);
    }
}
