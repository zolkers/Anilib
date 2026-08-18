package fr.vriege.anilib.feature.reader;

public interface ReaderContentRegistrar {
    AutoCloseable register(ReaderContentProvider provider);
}
