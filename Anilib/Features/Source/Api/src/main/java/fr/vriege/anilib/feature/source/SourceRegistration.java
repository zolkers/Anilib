package fr.vriege.anilib.feature.source;

public interface SourceRegistration extends AutoCloseable {
    SourceId sourceId();

    @Override
    void close();
}
