package fr.vriege.anilib.tooling.javaquality;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/** Prevents source and tracker extensions from bypassing their capability-limited factory context. */
public final class ExtensionIsolationRule implements AnilibJavaRule {
    private static final String SOURCE_SDK = "feature.source.api";
    private static final String TRACKER_SDK = "feature.tracker.api";
    private static final Set<String> COMMON_DEPENDENCIES = Set.of(
            "foundation",
            "framework.http.api");
    private static final Pattern FORBIDDEN_ACCESS = Pattern.compile(
            "\\b(?:java\\.net\\.http\\."
                    + "|java\\.net\\.(?:URL|URLConnection|HttpURLConnection|Socket|ServerSocket|Datagram)"
                    + "|java\\.nio\\.file\\."
                    + "|java\\.lang\\.reflect\\."
                    + "|java\\.io\\.File"
                    + "|TRUSTED_PLATFORM_RUNTIME"
                    + "|fr\\.vriege\\.anilib\\.feature\\.network\\."
                    + "|fr\\.vriege\\.anilib\\.kernel\\.)");

    public ExtensionIsolationRule() {
    }

    @Override
    public String name() {
        return "extension-isolation";
    }

    @Override
    public List<Diagnostic> analyze(RepositorySnapshot repository) {
        List<Diagnostic> diagnostics = new ArrayList<>();
        for (ModuleMetadata module : repository.modules()) {
            if (module.layer() != ModuleMetadata.Layer.EXTENSION) {
                continue;
            }
            validateModule(module, diagnostics);
            repository.javaSources().stream()
                    .filter(source -> source.module().id().equals(module.id()))
                    .forEach(source -> validateSource(source, diagnostics));
        }
        return List.copyOf(diagnostics);
    }

    private void validateModule(ModuleMetadata module, List<Diagnostic> diagnostics) {
        if (!module.role().equals("BUNDLE")) {
            diagnostics.add(diagnostic(module, "Extension module role must be BUNDLE"));
        }
        long sdkCount = module.dependencies().stream()
                .filter(dependency -> dependency.equals(SOURCE_SDK) || dependency.equals(TRACKER_SDK))
                .count();
        if (sdkCount != 1) {
            diagnostics.add(diagnostic(
                    module,
                    "Extension must depend on exactly one supported extension SDK"));
        }
        for (String dependency : module.dependencies()) {
            if (!COMMON_DEPENDENCIES.contains(dependency)
                    && !dependency.equals(SOURCE_SDK)
                    && !dependency.equals(TRACKER_SDK)) {
                diagnostics.add(diagnostic(module,
                        "Extension dependency bypasses isolation: " + dependency));
            }
        }
    }

    private void validateSource(JavaSource source, List<Diagnostic> diagnostics) {
        for (int index = 0; index < source.lines().size(); index++) {
            if (FORBIDDEN_ACCESS.matcher(source.lines().get(index)).find()) {
                diagnostics.add(new Diagnostic(
                        name(),
                        source.path(),
                        index + 1,
                        "Extension must use only its granted extension context"));
            }
        }
    }

    private Diagnostic diagnostic(ModuleMetadata module, String message) {
        return new Diagnostic(name(), module.manifestPath(), 1, message);
    }
}
