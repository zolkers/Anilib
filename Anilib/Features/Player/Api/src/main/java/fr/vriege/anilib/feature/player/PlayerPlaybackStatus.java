package fr.vriege.anilib.feature.player;

/** Portable playback lifecycle independent of Media3 or native desktop APIs. */
public enum PlayerPlaybackStatus {
    UNAVAILABLE,
    LOADING,
    PAUSED,
    PLAYING,
    ENDED,
    FAILED
}
