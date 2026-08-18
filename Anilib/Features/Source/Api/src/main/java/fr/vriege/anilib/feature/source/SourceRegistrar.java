package fr.vriege.anilib.feature.source;

public interface SourceRegistrar {
    SourceRegistration register(Source source);

    SourceRegistration register(SourceExtensionManifest manifest, Source source);
}
