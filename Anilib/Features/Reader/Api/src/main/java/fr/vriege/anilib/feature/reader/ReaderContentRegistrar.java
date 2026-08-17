package fr.vriege.anilib.feature.reader;

/** Installation-only registration seam for one removable alternate content provider. */
public interface ReaderContentRegistrar {
    AutoCloseable register(ReaderContentProvider provider);
}
