package fr.vriege.anilib.feature.library.ui;

import fr.vriege.anilib.feature.library.LibraryCategory;
import fr.vriege.anilib.feature.library.LibraryCategoryUpdatePolicy;
import fr.vriege.anilib.feature.library.LibraryDisplayDensity;
import fr.vriege.anilib.feature.library.LibraryDisplayMode;
import fr.vriege.anilib.feature.library.LibraryItemId;
import fr.vriege.anilib.feature.library.LibrarySort;

import java.util.Optional;
import java.util.Set;

public interface LibraryPresentation {
    LibraryOverview library();

    Optional<LibraryDetails> details(LibraryItemId id);

    LibraryHistory history();

    void setDisplayMode(LibraryDisplayMode mode);

    void setDisplayDensity(LibraryDisplayDensity density);

    void setSort(LibrarySort sort);

    void setDefaultCategory(Optional<String> category);

    void createCategory(String name);

    void renameCategory(String currentName, String nextName);

    void moveCategory(String name, int targetIndex);

    void deleteCategory(String name);

    void updateCategory(LibraryCategory category);

    void setCategoryUpdatePolicy(String name, LibraryCategoryUpdatePolicy policy);

    void setFavorite(Set<LibraryItemId> ids, boolean favorite);

    void addToCategory(Set<LibraryItemId> ids, String category);

    void removeFromCategory(Set<LibraryItemId> ids, String category);

    void deleteTitles(Set<LibraryItemId> ids);
}
