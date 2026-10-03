package fr.vriege.anilib.feature.covercache;

import java.util.Optional;

public interface CoverCache {
    DecodedImage load(CoverKey key, CoverLoader loader);

    Optional<DecodedImage> find(CoverKey key);

    byte[] loadEncoded(CoverKey key, CoverLoader loader);

    Optional<byte[]> findEncoded(CoverKey key);

    void invalidate(CoverKey key);
}
