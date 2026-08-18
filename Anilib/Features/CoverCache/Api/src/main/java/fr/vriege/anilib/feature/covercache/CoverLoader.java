package fr.vriege.anilib.feature.covercache;

import java.io.IOException;

@FunctionalInterface
public interface CoverLoader {
    byte[] load() throws IOException;
}
