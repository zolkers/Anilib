package fr.vriege.anilib.feature.tracker;

public interface TrackerRegistrar {
    TrackerRegistration register(TrackerExtensionManifest manifest, Tracker tracker);
}
