package fr.vriege.anilib.feature.tracker;

/** Constructs one tracker adapter using only its declared host capabilities. */
@FunctionalInterface
public interface TrackerExtensionFactory {
    Tracker create(TrackerExtensionContext context);
}
