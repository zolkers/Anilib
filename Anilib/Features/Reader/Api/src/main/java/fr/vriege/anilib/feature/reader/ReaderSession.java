package fr.vriege.anilib.feature.reader;

/** One mutable, closeable reading session backed by a bounded page pipeline. */
public interface ReaderSession extends AutoCloseable {
    ReaderSessionSnapshot snapshot();

    byte[] currentPage();

    /** Loads any page through the same bounded cache without changing progress. */
    byte[] page(int index);

    void goToPage(int index);

    boolean nextPage();

    boolean previousPage();

    void setDirection(ReadingDirection direction);

    @Override
    void close();
}
