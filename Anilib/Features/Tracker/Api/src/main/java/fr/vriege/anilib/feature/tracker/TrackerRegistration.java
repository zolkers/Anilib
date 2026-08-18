package fr.vriege.anilib.feature.tracker;

public interface TrackerRegistration extends AutoCloseable {
    TrackerId trackerId();

    @Override
    void close();
}
