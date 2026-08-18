package fr.vriege.anilib.feature.tracker;

/** Lifecycle handle removing exactly one tracker registration. */
public interface TrackerRegistration extends AutoCloseable {
    TrackerId trackerId();

    @Override
    void close();
}
