package fr.vriege.anilib.tooling.javaquality;

import java.util.List;

public final class AnilibJavaRuleRegistry {
    private AnilibJavaRuleRegistry() {
    }

    public static List<AnilibJavaRule> standard() {
        return List.of(
                new PackageLayoutRule(),
                new DirectTypeQualifierRule(),
                new EmptyModuleRule(),
                new ImportPolicyRule(),
                new ModuleArchitectureRule(),
                new BuildDependencyRule(),
                new AndroidRuntimeApiRule(),
                new DesktopReleaseRule(),
                new AndroidReleaseRule(),
                new ApplicationReleaseRule(),
                new SecurityBoundaryRule(),
                new LocalizationRule(),
                new ExtensionIsolationRule(),
                new KotlinSourceRule(),
                new SourceFormatRule());
    }
}
