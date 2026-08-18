package fr.vriege.anilib.tooling.javaquality;

import java.util.List;

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
                new AndroidReleaseRule(),
                new ApplicationReleaseRule(),
                new SecurityBoundaryRule(),
                new ExtensionIsolationRule(),
                new KotlinSourceRule(),
                new SourceFormatRule());
    }
}
