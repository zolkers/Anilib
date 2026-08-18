package fr.vriege.anilib.feature.tracker;

/** Portable Aniyomi-style tracking state mapped to provider-specific values by adapters. */
public enum TrackerStatus {
    WATCHING,
    READING,
    COMPLETED,
    ON_HOLD,
    PLANNING,
    DROPPED,
    REWATCHING,
    REREADING
}
