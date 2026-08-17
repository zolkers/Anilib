package fr.vriege.anilib.feature.covercache;

import java.util.Optional;

/** Durable decoded-cover cache contract. */
public interface CoverCache {
    DecodedImage load(CoverKey key, CoverLoader loader);

    Optional<DecodedImage> find(CoverKey key);

    void invalidate(CoverKey key);
}
