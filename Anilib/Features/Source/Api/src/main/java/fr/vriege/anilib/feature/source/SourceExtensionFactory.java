package fr.vriege.anilib.feature.source;

@FunctionalInterface
public interface SourceExtensionFactory {
    Source create(SourceExtensionContext context);
}
