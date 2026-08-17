package fr.vriege.anilib.tooling.javaquality;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Validates module metadata, layer direction, and direct source dependencies. */
public final class ModuleArchitectureRule implements AnilibJavaRule {
    private static final Pattern ID = Pattern.compile("[a-z][a-z0-9]*(?:[.-][a-z0-9]+)*");
    private static final Pattern IMPORT = Pattern.compile("^\\s*import\\s+(?:static\\s+)?([^;]+)\\s*;");

    public ModuleArchitectureRule() {
    }

    @Override
    public String name() {
        return "module-architecture";
    }

    @Override
    public List<Diagnostic> analyze(RepositorySnapshot repository) {
        List<Diagnostic> diagnostics = new ArrayList<>();
        Map<String, ModuleMetadata> modules = indexModules(repository, diagnostics);
        validateManifests(modules, diagnostics);
        validateSourceOwnership(repository, modules, diagnostics);
        return diagnostics;
    }

    private Map<String, ModuleMetadata> indexModules(
            RepositorySnapshot repository,
            List<Diagnostic> diagnostics) {
        Map<String, ModuleMetadata> modules = new HashMap<>();
        for (ModuleMetadata module : repository.modules()) {
            if (!ID.matcher(module.id()).matches()) {
                diagnostics.add(diagnostic(module, "Invalid module id: " + module.id()));
            }
            if (modules.putIfAbsent(module.id(), module) != null) {
                diagnostics.add(diagnostic(module, "Duplicate module id: " + module.id()));
            }
            if (module.language() == ModuleMetadata.Language.JAVA
                    && !Files.isRegularFile(module.directory().resolve("src/main/java/module-info.java"))) {
                diagnostics.add(diagnostic(module, "Source-owning module must declare module-info.java"));
            }
            if (module.language() != ModuleMetadata.Language.JAVA
                    && module.layer() != ModuleMetadata.Layer.PLATFORM) {
                diagnostics.add(diagnostic(module, "Kotlin is restricted to platform UI adapters"));
            }
        }
        return modules;
    }

    private void validateManifests(
            Map<String, ModuleMetadata> modules,
            List<Diagnostic> diagnostics) {
        for (ModuleMetadata module : modules.values()) {
            Set<String> uniqueDependencies = new HashSet<>();
            for (String dependencyId : module.dependencies()) {
                if (!uniqueDependencies.add(dependencyId)) {
                    diagnostics.add(diagnostic(module, "Duplicate dependency: " + dependencyId));
                }
                ModuleMetadata dependency = modules.get(dependencyId);
                if (dependency == null) {
                    diagnostics.add(diagnostic(module, "Unknown dependency: " + dependencyId));
                    continue;
                }
                validateDirection(module, dependency, diagnostics);
            }
        }
    }

    private void validateDirection(
            ModuleMetadata source,
            ModuleMetadata target,
            List<Diagnostic> diagnostics) {
        if (source.id().equals(target.id())) {
            diagnostics.add(diagnostic(source, "Module cannot depend on itself"));
            return;
        }
        if (source.layer() != ModuleMetadata.Layer.TOOLING
                && target.layer() == ModuleMetadata.Layer.TOOLING) {
            diagnostics.add(diagnostic(source, "Production module cannot depend on Tooling: " + target.id()));
        }
        if (source.layer() != ModuleMetadata.Layer.TOOLING
                && target.layer().rank() > source.layer().rank()) {
            diagnostics.add(diagnostic(source,
                    source.layer() + " cannot depend outward on " + target.layer() + ": " + target.id()));
        }
        if (source.layer() == ModuleMetadata.Layer.FEATURE
                && target.layer() == ModuleMetadata.Layer.FEATURE
                && !source.owner().equals(target.owner())
                && !target.role().equals("API")) {
            diagnostics.add(diagnostic(source,
                    "A feature may depend only on another feature's API: " + target.id()));
        }
    }

    private void validateSourceOwnership(
            RepositorySnapshot repository,
            Map<String, ModuleMetadata> modules,
            List<Diagnostic> diagnostics) {
        List<PackageOwner> packages = repository.javaSources().stream()
                .filter(source -> !source.isModuleDescriptor())
                .flatMap(source -> source.packageName().stream()
                        .map(packageName -> new PackageOwner(packageName, source.module())))
                .distinct()
                .sorted(Comparator.comparingInt((PackageOwner owner) -> owner.packageName().length()).reversed())
                .toList();

        for (JavaSource source : repository.javaSources()) {
            if (source.isModuleDescriptor()) {
                continue;
            }
            Set<String> declared = Set.copyOf(source.module().dependencies());
            for (int index = 0; index < source.lines().size(); index++) {
                Matcher matcher = IMPORT.matcher(source.lines().get(index));
                if (!matcher.matches() || !matcher.group(1).startsWith("fr.vriege.anilib.")) {
                    continue;
                }
                ModuleMetadata target = ownerOf(matcher.group(1), packages);
                if (target != null
                        && !target.id().equals(source.module().id())
                        && modules.containsKey(target.id())
                        && !declared.contains(target.id())) {
                    diagnostics.add(new Diagnostic(name(), source.path(), index + 1,
                            "Direct import requires manifest dependency " + target.id()));
                }
            }
        }
    }

    private static ModuleMetadata ownerOf(String imported, List<PackageOwner> packages) {
        for (PackageOwner owner : packages) {
            if (imported.startsWith(owner.packageName() + ".")) {
                return owner.module();
            }
        }
        return null;
    }

    private Diagnostic diagnostic(ModuleMetadata module, String message) {
        return new Diagnostic(name(), module.manifestPath(), 1, message);
    }

    private record PackageOwner(String packageName, ModuleMetadata module) {
    }
}
