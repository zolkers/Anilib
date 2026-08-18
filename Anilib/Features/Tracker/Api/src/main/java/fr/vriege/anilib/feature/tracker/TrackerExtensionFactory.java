package fr.vriege.anilib.feature.tracker;

@FunctionalInterface
public interface TrackerExtensionFactory {
    Tracker create(TrackerExtensionContext context);
}
