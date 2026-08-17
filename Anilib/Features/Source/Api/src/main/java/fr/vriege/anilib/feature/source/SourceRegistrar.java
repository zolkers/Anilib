package fr.vriege.anilib.feature.source;

/** Installation-only port used by explicitly selected source Bundles. */
public interface SourceRegistrar {
    SourceRegistration register(Source source);
}
