package fr.vriege.anilib.feature.tracker;

/** Installation-only port used by selected tracker bundles. */
public interface TrackerRegistrar {
    TrackerRegistration register(TrackerExtensionManifest manifest, Tracker tracker);
}
