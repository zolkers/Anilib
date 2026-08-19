package fr.vriege.anilib.tooling.javaquality;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class AndroidRuntimeApiRule implements AnilibJavaRule {
    private static final Map<String, String> UNSUPPORTED_SHARED_APIS = Map.of(
            "Thread.ofVirtual(", "virtual threads",
            "Thread.ofPlatform(", "Java 21 thread builders",
            "Thread.startVirtualThread(", "virtual threads",
            "Executors.newVirtualThreadPerTaskExecutor(", "virtual-thread executors",
            "Executors.newThreadPerTaskExecutor(", "Java 21 per-task executors");

    public AndroidRuntimeApiRule() {
    }

    @Override
    public String name() {
        return "android-runtime-api";
    }

    @Override
    public List<Diagnostic> analyze(RepositorySnapshot repository) {
        List<Diagnostic> diagnostics = new ArrayList<>();
        for (JavaSource source : repository.javaSources()) {
            if (!runsOnAndroid(source.module())) {
                continue;
            }
            for (int index = 0; index < source.lines().size(); index++) {
                String line = source.lines().get(index);
                for (Map.Entry<String, String> api : UNSUPPORTED_SHARED_APIS.entrySet()) {
                    if (line.contains(api.getKey())) {
                        diagnostics.add(new Diagnostic(
                                name(),
                                source.path(),
                                index + 1,
                                "Shared code cannot use " + api.getValue() + " because Android lacks this API"));
                    }
                }
            }
        }
        return diagnostics;
    }

    private static boolean runsOnAndroid(ModuleMetadata module) {
        return module.layer() != ModuleMetadata.Layer.PLATFORM
                && module.layer() != ModuleMetadata.Layer.TOOLING;
    }
}
