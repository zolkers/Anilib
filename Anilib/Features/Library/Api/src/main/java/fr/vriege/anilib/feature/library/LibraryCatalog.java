package fr.vriege.anilib.feature.library;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface LibraryCatalog {
    List<LibraryItem> snapshot();

    Optional<LibraryItem> find(LibraryItemId id);

    void save(LibraryItem item);

    void replaceAll(Collection<LibraryItem> items);

    boolean remove(LibraryItemId id);
}
