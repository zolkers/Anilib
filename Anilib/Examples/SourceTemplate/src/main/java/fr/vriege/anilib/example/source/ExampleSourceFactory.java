package fr.vriege.anilib.example.source;

import fr.vriege.anilib.feature.source.Source;
import fr.vriege.anilib.feature.source.SourceExtensionContext;
import fr.vriege.anilib.feature.source.SourceExtensionFactory;

/** Public no-argument entrypoint named by the portable Bundle descriptor. */
public final class ExampleSourceFactory implements SourceExtensionFactory {
    public ExampleSourceFactory() {
    }

    @Override
    public Source create(SourceExtensionContext context) {
        return new ExampleCatalogueSource();
    }
}
