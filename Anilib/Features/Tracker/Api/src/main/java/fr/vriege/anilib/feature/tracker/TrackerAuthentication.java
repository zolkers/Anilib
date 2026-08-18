package fr.vriege.anilib.feature.tracker;

/** Authentication workflow exposed by one tracker adapter. */
public enum TrackerAuthentication {
    NONE,
    USERNAME_PASSWORD,
    TOKEN,
    OAUTH
}
