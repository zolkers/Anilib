package fr.vriege.anilib.feature.reader.ui;

import fr.vriege.anilib.feature.source.SourceContentUnit;

import java.util.Objects;

public record ReaderWindowChapter(
        SourceContentUnit contentUnit,
        int pageCount,
        int firstGlobalPage,
        boolean current) {

    public ReaderWindowChapter {
        Objects.requireNonNull(contentUnit, "contentUnit must not be null");
        if (pageCount < 1) {
            throw new IllegalArgumentException("pageCount must be positive");
        }
        if (firstGlobalPage < 0) {
            throw new IllegalArgumentException("firstGlobalPage must not be negative");
        }
    }

    public boolean contains(int globalPage) {
        return globalPage >= firstGlobalPage && globalPage < firstGlobalPage + pageCount;
    }

    public int localPage(int globalPage) {
        return globalPage - firstGlobalPage;
    }
}
