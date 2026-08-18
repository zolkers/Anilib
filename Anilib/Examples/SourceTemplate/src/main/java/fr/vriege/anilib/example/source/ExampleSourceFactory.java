package fr.vriege.anilib.example.source;

import fr.vriege.anilib.feature.source.Source;
import fr.vriege.anilib.feature.source.SourceExtensionContext;
import fr.vriege.anilib.feature.source.SourceExtensionFactory;

public final class ExampleSourceFactory implements SourceExtensionFactory {
    public ExampleSourceFactory() {
    }

    @Override
    public Source create(SourceExtensionContext context) {
        return new ExampleCatalogueSource();
    }
}
