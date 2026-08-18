package fr.vriege.anilib.feature.reader;

public interface ReaderSession extends AutoCloseable {
    ReaderSessionSnapshot snapshot();

    byte[] currentPage();

    byte[] page(int index);

    void goToPage(int index);

    boolean nextPage();

    boolean previousPage();

    void setDirection(ReadingDirection direction);

    @Override
    void close();
}
