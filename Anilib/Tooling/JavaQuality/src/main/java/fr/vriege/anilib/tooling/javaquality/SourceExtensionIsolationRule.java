package fr.vriege.anilib.tooling.javaquality;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/** Prevents source extension modules from bypassing their capability-limited factory context. */
public final class SourceExtensionIsolationRule implements AnilibJavaRule {
    private static final Set<String> ALLOWED_DEPENDENCIES = Set.of(
            "foundation",
            "framework.http.api",
            "feature.source.api");
    private static final Pattern FORBIDDEN_ACCESS = Pattern.compile(
            "\\b(?:java\\.net\\.http\\."
                    + "|java\\.net\\.(?:URL|URLConnection|HttpURLConnection|Socket|ServerSocket|Datagram)"
                    + "|java\\.nio\\.file\\."
                    + "|java\\.lang\\.reflect\\."
                    + "|java\\.io\\.File"
                    + "|fr\\.vriege\\.anilib\\.feature\\.network\\."
                    + "|fr\\.vriege\\.anilib\\.kernel\\.)");

    public SourceExtensionIsolationRule() {
    }

    @Override
    public String name() {
        return "source-extension-isolation";
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
            diagnostics.add(diagnostic(module, "Source extension module role must be BUNDLE"));
        }
        if (!module.dependencies().contains("feature.source.api")) {
            diagnostics.add(diagnostic(module, "Source extension must depend on feature.source.api"));
        }
        for (String dependency : module.dependencies()) {
            if (!ALLOWED_DEPENDENCIES.contains(dependency)) {
                diagnostics.add(diagnostic(module,
                        "Source extension dependency bypasses isolation: " + dependency));
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
                        "Source extension must use only its granted SourceExtensionContext"));
            }
        }
    }

    private Diagnostic diagnostic(ModuleMetadata module, String message) {
        return new Diagnostic(name(), module.manifestPath(), 1, message);
    }
}
