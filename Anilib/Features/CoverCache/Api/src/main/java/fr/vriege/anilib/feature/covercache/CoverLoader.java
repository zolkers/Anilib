package fr.vriege.anilib.feature.covercache;

import java.io.IOException;

/** Obtains encoded image bytes when a cover is absent from the cache. */
@FunctionalInterface
public interface CoverLoader {
    byte[] load() throws IOException;
}
