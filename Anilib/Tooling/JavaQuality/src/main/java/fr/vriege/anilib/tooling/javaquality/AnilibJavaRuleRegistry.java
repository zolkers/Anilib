package fr.vriege.anilib.tooling.javaquality;

import java.util.List;

/** Canonical rule order used by the CLI and architecture suite. */
public final class AnilibJavaRuleRegistry {
    private AnilibJavaRuleRegistry() {
    }

    public static List<AnilibJavaRule> standard() {
        return List.of(
                new PackageLayoutRule(),
                new ImportPolicyRule(),
                new ModuleArchitectureRule(),
                new BuildDependencyRule(),
                new DesktopReleaseRule(),
                new ExtensionIsolationRule(),
                new KotlinSourceRule(),
                new SourceFormatRule());
    }
}
