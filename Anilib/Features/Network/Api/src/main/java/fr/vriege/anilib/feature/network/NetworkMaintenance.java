package fr.vriege.anilib.feature.network;

/** User-facing maintenance operations owned by the shared network stack. */
public interface NetworkMaintenance {
    void clearCookies();

    void clearResponseCache();
}
