package fr.vriege.anilib.feature.source;

/** Creates one source using only the capabilities granted by its extension manifest. */
@FunctionalInterface
public interface SourceExtensionFactory {
    Source create(SourceExtensionContext context);
}
