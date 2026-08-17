package fr.vriege.anilib.feature.source;

/** Lifecycle handle owned by the Bundle that registered a source. */
public interface SourceRegistration extends AutoCloseable {
    SourceId sourceId();

    @Override
    void close();
}
