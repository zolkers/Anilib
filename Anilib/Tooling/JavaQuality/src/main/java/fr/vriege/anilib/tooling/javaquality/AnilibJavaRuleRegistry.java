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
                new DesktopReleaseRule(),
                new ApplicationReleaseRule(),
                new SecurityBoundaryRule(),
                new CatalogueNavigationMutationRule(),
                new CatalogueDetailsRouteRule(),
                new LocalizationRule(),
                new ExtensionIsolationRule(),
                new ThreadOwnershipRule(),
                new KotlinSourceRule(),
                new SourceFormatRule());
    }
}
